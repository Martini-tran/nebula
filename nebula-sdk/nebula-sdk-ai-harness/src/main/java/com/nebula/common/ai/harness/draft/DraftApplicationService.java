package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.config.HarnessCommitProperties;
import com.nebula.common.ai.harness.config.HarnessDraftProperties;
import com.nebula.common.ai.harness.simulate.DraftSimulator;
import com.nebula.common.ai.harness.simulate.SimulationReport;
import com.nebula.common.ai.harness.validate.DraftValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 草稿应用服务：统一执行访问控制、深拷贝 mutation、字段校验、预算检查与 revision CAS。
 *
 * <p>所有工具必须经过本服务，禁止直接操作 Mapper 或跨 mutation 持有 {@link FlowDraft}。
 *
 * @author nebula
 */
@Slf4j
public class DraftApplicationService {

    private static final Set<String> METADATA_FIELDS = Set.of(
            "name", "description", "flowCode", "defaultProfileCode", "maxTransitions");
    private static final Set<String> CLEARABLE_METADATA_FIELDS = Set.of(
            "name", "description", "flowCode", "defaultProfileCode");

    private final DraftStore store;
    private final FlowDefinitionCodec codec;
    private final DraftNodeConverter nodeConverter;
    private final DraftFieldValidator validator;
    private final DraftValidator fullValidator;
    private final DraftSimulator simulator;
    private final DraftCommitter committer;
    private final ConditionCompiler conditionCompiler;
    private final HarnessDraftProperties properties;
    private final HarnessCommitProperties commitProperties;
    private final ApplicationEventPublisher eventPublisher;

    public DraftApplicationService(DraftStore store,
                                   FlowDefinitionCodec codec,
                                   DraftNodeConverter nodeConverter,
                                   DraftFieldValidator validator,
                                   DraftValidator fullValidator,
                                   DraftSimulator simulator,
                                   DraftCommitter committer,
                                   ConditionCompiler conditionCompiler,
                                   HarnessDraftProperties properties,
                                   HarnessCommitProperties commitProperties,
                                   ApplicationEventPublisher eventPublisher) {
        this.store = store;
        this.codec = codec;
        this.nodeConverter = nodeConverter;
        this.validator = validator;
        this.fullValidator = fullValidator;
        this.simulator = simulator;
        this.committer = committer;
        this.conditionCompiler = conditionCompiler;
        this.properties = properties;
        this.commitProperties = commitProperties;
        this.eventPublisher = eventPublisher;
    }

    public DraftOperationResult create(DraftAccess access,
                                       String engineType,
                                       String flowCode,
                                       String name,
                                       String description) {
        String engine = normalizeEngine(engineType);
        if (!EngineTypeCatalog.supported(engine)) {
            return failure(null, null, "INVALID_ENGINE_TYPE", "engineType 必须为 DAG 或 STATE_MACHINE",
                    "先调用 list_node_types 选择执行内核");
        }
        FlowDefinition graph = new FlowDefinition()
                .setFlowCode(blankToNull(flowCode))
                .setName(blankToNull(name))
                .setDescription(blankToNull(description))
                .setEngineType(engine);
        FlowDraft draft = new FlowDraft()
                .setDraftId("d_" + UUID.randomUUID().toString().replace("-", ""))
                .setSessionId(blankToNull(access.sessionId()))
                .setUserId(access.userId())
                .setFlowCode(graph.getFlowCode())
                .setName(graph.getName())
                .setDescription(graph.getDescription())
                .setEngineType(engine)
                .setRevision(0)
                .setStatus(DraftStatus.BUILDING)
                .setGraph(graph);
        List<DraftIssue> metadataIssues = validateMetadata(draft);
        if (hasErrors(metadataIssues)) {
            return DraftOperationResult.failure(null, null, metadataIssues, Map.of());
        }
        FlowDraft created = store.create(draft);
        publishUpdated(created);
        log.info("创建流程草稿: draftId={}, userId={}, sessionId={}, engineType={}",
                created.getDraftId(), created.getUserId(), created.getSessionId(), created.getEngineType());
        return DraftOperationResult.success(created, Map.of(
                "engineType", engine,
                "constraints", EngineTypeCatalog.constraints(engine),
                "draft", summary(created)));
    }

    public DraftOperationResult updateMetadata(DraftAccess access,
                                               String draftId,
                                               long expectedRevision,
                                               Map<String, Object> patch,
                                               List<String> clearFields) {
        return mutate(access, draftId, expectedRevision, draft -> {
            List<DraftIssue> issues = validatePatchShape(null, patch, clearFields,
                    METADATA_FIELDS, CLEARABLE_METADATA_FIELDS);
            if (!issues.isEmpty()) {
                return issues;
            }
            for (String field : clearFields == null ? List.<String>of() : clearFields) {
                clearMetadata(draft, field);
            }
            if (patch != null) {
                for (Map.Entry<String, Object> entry : patch.entrySet()) {
                    applyMetadata(draft, entry.getKey(), entry.getValue());
                }
            }
            syncGraphMetadata(draft);
            return validateMetadata(draft);
        }, draft -> Map.of("changedFields", changedFields(patch, clearFields), "draft", summary(draft)));
    }

    public DraftOperationResult addNode(DraftAccess access,
                                        String draftId,
                                        long expectedRevision,
                                        Map<String, Object> fields) {
        return mutate(access, draftId, expectedRevision, draft -> {
            FlowNodeDefinition node;
            try {
                node = nodeConverter.create(fields);
            } catch (IllegalArgumentException e) {
                return List.of(DraftIssue.error("INVALID_TOOL_ARGUMENTS", null, null, e.getMessage(),
                        "按节点字段 schema 修正后重试"));
            }
            ensureLists(draft.getGraph());
            draft.getGraph().getNodes().add(node);
            return validator.validate(draft);
        }, draft -> Map.of("nodeCount", draft.getGraph().getNodes().size(), "draft", summary(draft)));
    }

    public DraftOperationResult updateNode(DraftAccess access,
                                           String draftId,
                                           long expectedRevision,
                                           String nodeCode,
                                           Map<String, Object> patch,
                                           List<String> clearFields) {
        return mutate(access, draftId, expectedRevision, draft -> {
            FlowNodeDefinition node = findNode(draft, nodeCode);
            if (node == null) {
                return List.of(DraftIssue.error("NODE_NOT_FOUND", nodeCode, "nodeCode",
                        "待更新节点不存在", "先调用 read_draft 核对 nodeCode"));
            }
            List<DraftIssue> issues = nodeConverter.patch(node, patch, clearFields);
            return hasErrors(issues) ? issues : mergeIssues(issues, validator.validate(draft));
        }, draft -> Map.of("nodeCode", nodeCode, "draft", summary(draft)));
    }

    public DraftOperationResult removeNode(DraftAccess access,
                                           String draftId,
                                           long expectedRevision,
                                           String nodeCode) {
        List<FlowEdgeDefinition> removedEdges = new ArrayList<>();
        return mutate(access, draftId, expectedRevision, draft -> {
            ensureLists(draft.getGraph());
            boolean removed = draft.getGraph().getNodes().removeIf(node -> Objects.equals(nodeCode, node.getNodeCode()));
            if (!removed) {
                return List.of(DraftIssue.error("NODE_NOT_FOUND", nodeCode, "nodeCode",
                        "待删除节点不存在", "先调用 read_draft 核对 nodeCode"));
            }
            draft.getGraph().getEdges().removeIf(edge -> {
                boolean incident = Objects.equals(nodeCode, edge.getFromNode())
                        || Objects.equals(nodeCode, edge.getToNode());
                if (incident) {
                    removedEdges.add(copyEdge(edge));
                }
                return incident;
            });
            return validator.validate(draft);
        }, draft -> Map.of(
                "removedNode", nodeCode,
                "removedEdges", edgeSummaries(removedEdges),
                "draft", summary(draft)));
    }

    public DraftOperationResult connect(DraftAccess access,
                                        String draftId,
                                        long expectedRevision,
                                        String fromNode,
                                        String toNode,
                                        String conditionExpr,
                                        String eventName,
                                        Integer sortNo) {
        return mutate(access, draftId, expectedRevision, draft -> {
            FlowNodeDefinition from = findNode(draft, fromNode);
            FlowNodeDefinition to = findNode(draft, toNode);
            if (from == null || to == null) {
                return List.of(DraftIssue.error("EDGE_ENDPOINT_NOT_FOUND", null, "fromNode/toNode",
                        "连边端点不存在: " + fromNode + " -> " + toNode, "先添加节点或核对节点编码"));
            }
            if (EngineTypeCatalog.STATE_MACHINE.equals(draft.getEngineType())
                    && "TERMINAL".equals(from.getStateType())) {
                return List.of(DraftIssue.error("TERMINAL_HAS_OUTGOING_EDGE", fromNode, "fromNode",
                        "TERMINAL 状态不能创建出边", "从其他非终态节点发起连接"));
            }
            try {
                conditionCompiler.compile(conditionExpr);
            } catch (RuntimeException e) {
                return List.of(DraftIssue.error("INVALID_CONDITION_EXPR", fromNode, "conditionExpr",
                        e.getMessage(), "修正 SpEL 表达式后重试"));
            }
            ensureLists(draft.getGraph());
            draft.getGraph().getEdges().add(new FlowEdgeDefinition()
                    .setFromNode(fromNode)
                    .setToNode(toNode)
                    .setConditionExpr(blankToNull(conditionExpr))
                    .setEventName(blankToNull(eventName))
                    .setSortNo(sortNo == null ? nextEdgeSortNo(draft) : sortNo));
            return validator.validate(draft);
        }, draft -> Map.of("edge", edgeSummary(findLastEdge(draft)), "draft", summary(draft)));
    }

    public DraftOperationResult disconnect(DraftAccess access,
                                           String draftId,
                                           long expectedRevision,
                                           String fromNode,
                                           String toNode,
                                           Integer sortNo,
                                           String conditionExpr,
                                           boolean conditionSpecified,
                                           String eventName,
                                           boolean eventSpecified) {
        List<FlowEdgeDefinition> removed = new ArrayList<>();
        return mutate(access, draftId, expectedRevision, draft -> {
            ensureLists(draft.getGraph());
            List<FlowEdgeDefinition> matches = draft.getGraph().getEdges().stream()
                    .filter(edge -> Objects.equals(fromNode, edge.getFromNode()))
                    .filter(edge -> Objects.equals(toNode, edge.getToNode()))
                    .filter(edge -> sortNo == null || sortNo == edge.getSortNo())
                    .filter(edge -> !conditionSpecified || Objects.equals(conditionExpr, edge.getConditionExpr()))
                    .filter(edge -> !eventSpecified || Objects.equals(eventName, edge.getEventName()))
                    .toList();
            if (matches.isEmpty()) {
                return List.of(DraftIssue.error("EDGE_NOT_FOUND", null, "fromNode/toNode",
                        "没有找到匹配的边", "调用 read_draft 查看边索引后重试"));
            }
            if (matches.size() > 1) {
                return List.of(DraftIssue.error("AMBIGUOUS_EDGE", null, "sortNo/conditionExpr/eventName",
                        "选择器命中 " + matches.size() + " 条边，未执行删除: " + edgeSummaries(matches),
                        "补充 sortNo、conditionExpr 或 eventName 后精确重试"));
            }
            FlowEdgeDefinition edge = matches.getFirst();
            removed.add(copyEdge(edge));
            draft.getGraph().getEdges().remove(edge);
            return validator.validate(draft);
        }, draft -> Map.of("removedEdge", edgeSummary(removed.getFirst()), "draft", summary(draft)));
    }

    public DraftOperationResult read(DraftAccess access,
                                     String draftId,
                                     List<String> nodeCodes,
                                     Integer edgePage,
                                     Integer edgePageSize) {
        FlowDraft draft = loadAuthorized(access, draftId);
        if (draft == null) {
            return notFound(draftId);
        }
        FlowDefinition graph = codec.copy(draft.getGraph());
        ensureLists(graph);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("draft", summary(draft));
        payload.put("metadata", metadata(draft));

        if (nodeCodes == null || nodeCodes.isEmpty()) {
            int limit = Math.min(properties.getMaxReadItems(), graph.getNodes().size());
            payload.put("nodes", graph.getNodes().subList(0, limit).stream().map(this::nodeSummary).toList());
            payload.put("nodesTruncated", graph.getNodes().size() > limit);
        } else {
            Set<String> requested = new LinkedHashSet<>(nodeCodes);
            if (requested.size() > properties.getMaxReadPageSize()) {
                return failure(draftId, draft.getRevision(), "DRAFT_SIZE_LIMIT",
                        "单次节点详情最多读取 " + properties.getMaxReadPageSize() + " 个",
                        "缩小 nodeCodes 后分页读取");
            }
            payload.put("nodes", graph.getNodes().stream()
                    .filter(node -> requested.contains(node.getNodeCode())).toList());
        }

        int page = edgePage == null ? 0 : Math.max(0, edgePage);
        int size = edgePageSize == null ? Math.min(50, properties.getMaxReadPageSize())
                : Math.max(1, Math.min(edgePageSize, properties.getMaxReadPageSize()));
        int from = Math.min(page * size, graph.getEdges().size());
        int to = Math.min(from + size, graph.getEdges().size());
        payload.put("edges", graph.getEdges().subList(from, to).stream().map(this::edgeSummary).toList());
        payload.put("edgePage", page);
        payload.put("edgePageSize", size);
        payload.put("edgeTotal", graph.getEdges().size());
        payload.put("hasMoreEdges", to < graph.getEdges().size());
        if (codec.byteSize(payload) > properties.getMaxToolResultBytes()) {
            return failure(draftId, draft.getRevision(), "DRAFT_SIZE_LIMIT",
                    "本次读取结果超过 " + properties.getMaxToolResultBytes() + " 字节预算",
                    "缩小 nodeCodes 或 edgePageSize 后重试");
        }
        return DraftOperationResult.success(draft, payload);
    }

    /**
     * 对指定 revision 做全量校验。校验本身不推进 revision，只有无 ERROR 时才原子记录校验标记。
     */
    public DraftOperationResult validate(DraftAccess access, String draftId, long expectedRevision) {
        FlowDraft draft = loadAuthorized(access, draftId);
        DraftOperationResult precondition = checkRevisionAndMutable(draft, draftId, expectedRevision);
        if (precondition != null) {
            return precondition;
        }

        List<DraftIssue> issues = fullValidator.validate(draft);
        Map<String, Object> payload = validationPayload(issues, draft.getRevision());
        if (hasErrors(issues)) {
            log.info("流程草稿校验未通过: draftId={}, userId={}, revision={}, errors={}",
                    draftId, draft.getUserId(), draft.getRevision(), issueCount(issues, "ERROR"));
            return DraftOperationResult.failure(draftId, draft.getRevision(), issues, payload);
        }
        if (!store.markValidated(draftId, draft.getUserId(), expectedRevision)) {
            return resolveWriteConflict(access, draftId);
        }
        draft.setLastValidatedRevision(expectedRevision);
        log.info("流程草稿校验通过: draftId={}, userId={}, revision={}, warnings={}",
                draftId, draft.getUserId(), draft.getRevision(), issueCount(issues, "WARN"));
        return DraftOperationResult.success(draft, issues, payload);
    }

    /**
     * 重新校验并在深拷贝上执行零副作用模拟。只有无 ERROR 的报告才会原子标记当前 revision。
     */
    public DraftOperationResult simulate(DraftAccess access,
                                         String draftId,
                                         long expectedRevision,
                                         Map<String, Object> initialInput) {
        FlowDraft draft = loadAuthorized(access, draftId);
        DraftOperationResult precondition = checkRevisionAndMutable(draft, draftId, expectedRevision);
        if (precondition != null) {
            return precondition;
        }
        if (simulator == null) {
            return failure(draftId, draft.getRevision(), "DRAFT_SIMULATOR_UNAVAILABLE",
                    "当前宿主没有提供 DraftSimulator", "检查 Harness 模拟器自动装配");
        }

        List<DraftIssue> validationIssues = fullValidator.validate(draft);
        if (hasErrors(validationIssues)) {
            return DraftOperationResult.failure(draftId, draft.getRevision(), validationIssues,
                    validationPayload(validationIssues, draft.getRevision()));
        }
        SimulationReport report = simulator.simulate(codec.copy(draft.getGraph()), initialInput);
        List<DraftIssue> issues = mergeIssues(validationIssues, report.issues());
        Map<String, Object> payload = report.toPayload(expectedRevision);
        if (!report.successful()) {
            log.info("流程草稿模拟未通过: draftId={}, userId={}, revision={}, engineType={}, errors={}",
                    draftId, draft.getUserId(), expectedRevision, draft.getEngineType(), issueCount(issues, "ERROR"));
            return DraftOperationResult.failure(draftId, draft.getRevision(), issues, payload);
        }
        if (!store.markSimulated(draftId, draft.getUserId(), expectedRevision)) {
            return resolveWriteConflict(access, draftId);
        }
        draft.setLastValidatedRevision(expectedRevision);
        draft.setLastSimulatedRevision(expectedRevision);
        log.info("流程草稿模拟通过: draftId={}, userId={}, revision={}, engineType={}, confidence={}, warnings={}",
                draftId, draft.getUserId(), expectedRevision, draft.getEngineType(), report.confidence(),
                issueCount(issues, "WARN"));
        return DraftOperationResult.success(draft, issues, payload);
    }

    /**
     * 重新校验并提交当前 revision。flowCode 只能由 metadata mutation 设置，提交动作不接受临时覆盖。
     */
    public DraftOperationResult commit(DraftAccess access, String draftId, long expectedRevision) {
        FlowDraft draft = loadAuthorized(access, draftId);
        DraftOperationResult precondition = checkRevisionAndMutable(draft, draftId, expectedRevision);
        if (precondition != null) {
            return precondition;
        }
        if (committer == null) {
            return failure(draftId, draft.getRevision(), "DRAFT_COMMITTER_UNAVAILABLE",
                    "当前宿主没有提供 DraftCommitter", "检查 manager 的提交适配器装配");
        }
        boolean requireSimulation = commitProperties == null || commitProperties.isRequireSimulation();
        if (requireSimulation && !Long.valueOf(expectedRevision).equals(draft.getLastSimulatedRevision())) {
            return failure(draftId, draft.getRevision(), "DRAFT_NOT_SIMULATED",
                    "当前 revision 尚未完成无 ERROR 模拟", "先调用 simulate_draft，再提交相同 revision");
        }

        List<DraftIssue> issues = fullValidator.validate(draft);
        if (hasErrors(issues)) {
            return DraftOperationResult.failure(draftId, draft.getRevision(), issues,
                    validationPayload(issues, draft.getRevision()));
        }
        if (!store.markValidated(draftId, draft.getUserId(), expectedRevision)) {
            return resolveWriteConflict(access, draftId);
        }
        draft.setLastValidatedRevision(expectedRevision);

        DraftCommitResult result = committer.commit(new DraftCommitRequest(
                draftId, draft.getUserId(), expectedRevision, requireSimulation, codec.copy(draft.getGraph())));
        if (!result.committed()) {
            return failure(draftId, draft.getRevision(), result.errorCode(), result.message(), result.hint());
        }

        FlowDraft committed = loadAuthorized(access, draftId);
        if (committed == null || committed.getStatus() != DraftStatus.COMMITTED) {
            return failure(draftId, draft.getRevision(), "DRAFT_COMMIT_FAILED",
                    "正式流程已写入但未能确认草稿终态", "查询流程和草稿状态后再处理");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flowCode", result.flowCode());
        payload.put("version", result.version());
        payload.put("committedRevision", expectedRevision);
        if (committed.getName() != null) {
            payload.put("name", committed.getName());
        }
        payload.put("draft", summary(committed));
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DraftCommittedEvent(
                    draftId, expectedRevision, result.flowCode(), result.version()));
        }
        log.info("流程草稿提交成功: draftId={}, userId={}, revision={}, flowCode={}, version={}",
                draftId, draft.getUserId(), expectedRevision, result.flowCode(), result.version());
        return DraftOperationResult.success(committed, issues, payload);
    }

    private DraftOperationResult mutate(DraftAccess access,
                                        String draftId,
                                        long expectedRevision,
                                        DraftMutation mutation,
                                        ResultPayload payloadFactory) {
        FlowDraft draft = loadAuthorized(access, draftId);
        if (draft == null) {
            return notFound(draftId);
        }
        if (draft.getStatus() != DraftStatus.BUILDING) {
            return failure(draftId, draft.getRevision(), "DRAFT_IMMUTABLE",
                    "草稿状态为 " + draft.getStatus() + "，不允许继续修改", "新建草稿后再操作");
        }
        if (draft.getRevision() != expectedRevision) {
            return conflict(draft);
        }

        // Store 已返回独立对象，再深拷贝图以隔离本次 mutation 与后续只读投影。
        Map<String, Object> originalSummary = summary(draft);
        draft.setGraph(codec.copy(draft.getGraph()));
        List<DraftIssue> issues;
        try {
            issues = mutation.apply(draft);
        } catch (IllegalArgumentException e) {
            return failure(draftId, draft.getRevision(), "INVALID_TOOL_ARGUMENTS", e.getMessage(),
                    "修正工具参数后重试");
        }
        if (hasErrors(issues)) {
            return DraftOperationResult.failure(draftId, draft.getRevision(), issues, Map.of("draft", originalSummary));
        }
        if (!store.compareAndSet(draft, expectedRevision)) {
            FlowDraft current = loadAuthorized(access, draftId);
            return current == null ? notFound(draftId)
                    : current.getStatus() == DraftStatus.BUILDING ? conflict(current)
                    : failure(draftId, current.getRevision(), "DRAFT_IMMUTABLE",
                            "草稿已进入终态 " + current.getStatus(), "新建草稿后再操作");
        }
        draft.setRevision(expectedRevision + 1);
        Map<String, Object> payload = payloadFactory.create(draft);
        publishUpdated(draft);
        log.info("流程草稿变更成功: draftId={}, userId={}, revision={}",
                draft.getDraftId(), draft.getUserId(), draft.getRevision());
        return DraftOperationResult.success(draft, issues, payload);
    }

    private DraftOperationResult checkRevisionAndMutable(FlowDraft draft,
                                                         String draftId,
                                                         long expectedRevision) {
        if (draft == null) {
            return notFound(draftId);
        }
        if (draft.getStatus() != DraftStatus.BUILDING) {
            return failure(draftId, draft.getRevision(), "DRAFT_IMMUTABLE",
                    "草稿状态为 " + draft.getStatus() + "，不允许继续校验或提交", "新建草稿后再操作");
        }
        return draft.getRevision() == expectedRevision ? null : conflict(draft);
    }

    private DraftOperationResult resolveWriteConflict(DraftAccess access, String draftId) {
        FlowDraft current = loadAuthorized(access, draftId);
        if (current == null) {
            return notFound(draftId);
        }
        return current.getStatus() == DraftStatus.BUILDING ? conflict(current)
                : failure(draftId, current.getRevision(), "DRAFT_IMMUTABLE",
                "草稿已进入终态 " + current.getStatus(), "新建草稿后再操作");
    }

    private Map<String, Object> validationPayload(List<DraftIssue> issues, long revision) {
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("revision", revision);
        validation.put("valid", !hasErrors(issues));
        validation.put("errorCount", issueCount(issues, "ERROR"));
        validation.put("warningCount", issueCount(issues, "WARN"));
        validation.put("infoCount", issueCount(issues, "INFO"));
        return Map.of("validation", validation);
    }

    private long issueCount(List<DraftIssue> issues, String level) {
        return issues.stream().filter(issue -> level.equals(issue.level())).count();
    }

    private FlowDraft loadAuthorized(DraftAccess access, String draftId) {
        FlowDraft draft = store.findOwned(draftId, access.userId());
        if (draft == null) {
            return null;
        }
        if (draft.getSessionId() != null && !Objects.equals(draft.getSessionId(), access.sessionId())) {
            log.warn("拒绝跨会话草稿访问: draftId={}, userId={}", draftId, access.userId());
            return null;
        }
        return draft;
    }

    private void publishUpdated(FlowDraft draft) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DraftUpdatedEvent(draft.getDraftId(), draft.getRevision(), summary(draft)));
        }
    }

    private List<DraftIssue> validateMetadata(FlowDraft draft) {
        List<DraftIssue> issues = new ArrayList<>();
        if (length(draft.getFlowCode()) > 64) {
            issues.add(fieldError("flowCode", "flowCode 不能超过 64 个字符"));
        }
        if (length(draft.getName()) > 128) {
            issues.add(fieldError("name", "name 不能超过 128 个字符"));
        }
        if (length(draft.getDescription()) > 512) {
            issues.add(fieldError("description", "description 不能超过 512 个字符"));
        }
        if (draft.getGraph().getMaxTransitions() <= 0) {
            issues.add(fieldError("maxTransitions", "maxTransitions 必须为正整数"));
        }
        return issues;
    }

    private List<DraftIssue> validatePatchShape(String nodeCode,
                                                Map<String, Object> patch,
                                                List<String> clearFields,
                                                Set<String> allowed,
                                                Set<String> clearable) {
        List<DraftIssue> issues = new ArrayList<>();
        Map<String, Object> values = patch == null ? Map.of() : patch;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                issues.add(DraftIssue.error("INVALID_TOOL_ARGUMENTS", nodeCode, entry.getKey(),
                        "字段不允许修改: " + entry.getKey(), "从 patch 移除该字段"));
            } else if (entry.getValue() == null) {
                issues.add(DraftIssue.error("INVALID_TOOL_ARGUMENTS", nodeCode, entry.getKey(),
                        "patch 不接受 null，请使用 clearFields", "改用 clearFields 清空"));
            }
        }
        for (String field : clearFields == null ? List.<String>of() : clearFields) {
            if (!clearable.contains(field)) {
                issues.add(DraftIssue.error("INVALID_TOOL_ARGUMENTS", nodeCode, field,
                        "字段不允许清空: " + field, "从 clearFields 移除该字段"));
            }
            if (values.containsKey(field)) {
                issues.add(DraftIssue.error("INVALID_TOOL_ARGUMENTS", nodeCode, field,
                        "同一字段不能同时出现在 patch 与 clearFields", "只保留一种更新方式"));
            }
        }
        return issues;
    }

    private void applyMetadata(FlowDraft draft, String field, Object value) {
        switch (field) {
            case "name" -> draft.setName(requireTextValue(field, value));
            case "description" -> draft.setDescription(requireTextValue(field, value));
            case "flowCode" -> draft.setFlowCode(requireTextValue(field, value));
            case "defaultProfileCode" -> draft.getGraph().setDefaultProfileCode(requireTextValue(field, value));
            case "maxTransitions" -> {
                if (!(value instanceof Number number)) {
                    throw new IllegalArgumentException("maxTransitions 必须是整数");
                }
                draft.getGraph().setMaxTransitions(number.intValue());
            }
            default -> throw new IllegalArgumentException("字段不允许修改: " + field);
        }
    }

    private void clearMetadata(FlowDraft draft, String field) {
        switch (field) {
            case "name" -> draft.setName(null);
            case "description" -> draft.setDescription(null);
            case "flowCode" -> draft.setFlowCode(null);
            case "defaultProfileCode" -> draft.getGraph().setDefaultProfileCode(null);
            default -> throw new IllegalArgumentException("字段不允许清空: " + field);
        }
    }

    private void syncGraphMetadata(FlowDraft draft) {
        draft.getGraph()
                .setFlowCode(draft.getFlowCode())
                .setName(draft.getName())
                .setDescription(draft.getDescription())
                .setEngineType(draft.getEngineType());
    }

    private Map<String, Object> summary(FlowDraft draft) {
        FlowDefinition graph = draft.getGraph();
        ensureLists(graph);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("draftId", draft.getDraftId());
        summary.put("revision", draft.getRevision());
        summary.put("status", draft.getStatus().name());
        summary.put("engineType", draft.getEngineType());
        summary.put("flowCode", draft.getFlowCode());
        summary.put("nodeCount", graph.getNodes().size());
        summary.put("edgeCount", graph.getEdges().size());
        summary.put("lastValidatedRevision", draft.getLastValidatedRevision());
        summary.put("lastSimulatedRevision", draft.getLastSimulatedRevision());
        return summary;
    }

    private Map<String, Object> metadata(FlowDraft draft) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("flowCode", draft.getFlowCode());
        metadata.put("name", draft.getName());
        metadata.put("description", draft.getDescription());
        metadata.put("engineType", draft.getEngineType());
        metadata.put("defaultProfileCode", draft.getGraph().getDefaultProfileCode());
        metadata.put("maxTransitions", draft.getGraph().getMaxTransitions());
        return metadata;
    }

    private Map<String, Object> nodeSummary(FlowNodeDefinition node) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nodeCode", node.getNodeCode());
        summary.put("name", node.getName());
        summary.put("nodeType", node.getNodeType());
        summary.put("stateType", node.getStateType());
        summary.put("outputKey", node.getOutputKey());
        summary.put("sortNo", node.getSortNo());
        return summary;
    }

    private Map<String, Object> edgeSummary(FlowEdgeDefinition edge) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fromNode", edge.getFromNode());
        summary.put("toNode", edge.getToNode());
        summary.put("conditionExpr", edge.getConditionExpr());
        summary.put("eventName", edge.getEventName());
        summary.put("sortNo", edge.getSortNo());
        return summary;
    }

    private List<Map<String, Object>> edgeSummaries(List<FlowEdgeDefinition> edges) {
        return edges.stream().map(this::edgeSummary).toList();
    }

    private FlowNodeDefinition findNode(FlowDraft draft, String nodeCode) {
        ensureLists(draft.getGraph());
        return draft.getGraph().getNodes().stream()
                .filter(node -> Objects.equals(nodeCode, node.getNodeCode()))
                .findFirst().orElse(null);
    }

    private FlowEdgeDefinition findLastEdge(FlowDraft draft) {
        return draft.getGraph().getEdges().getLast();
    }

    private int nextEdgeSortNo(FlowDraft draft) {
        return draft.getGraph().getEdges().stream()
                .map(FlowEdgeDefinition::getSortNo)
                .max(Comparator.naturalOrder()).orElse(-1) + 1;
    }

    private FlowEdgeDefinition copyEdge(FlowEdgeDefinition edge) {
        return new FlowEdgeDefinition()
                .setFromNode(edge.getFromNode())
                .setToNode(edge.getToNode())
                .setConditionExpr(edge.getConditionExpr())
                .setEventName(edge.getEventName())
                .setSortNo(edge.getSortNo());
    }

    private void ensureLists(FlowDefinition graph) {
        if (graph.getNodes() == null) {
            graph.setNodes(new ArrayList<>());
        }
        if (graph.getEdges() == null) {
            graph.setEdges(new ArrayList<>());
        }
    }

    private DraftOperationResult conflict(FlowDraft draft) {
        return failure(draft.getDraftId(), draft.getRevision(), "DRAFT_CONFLICT",
                "expectedRevision 与当前 revision 不一致", "调用 read_draft 获取最新 revision 后重试");
    }

    private DraftOperationResult notFound(String draftId) {
        return failure(draftId, null, "DRAFT_NOT_FOUND", "草稿不存在或当前身份无权访问",
                "核对 draftId，并在原用户和允许的会话中重试");
    }

    private DraftOperationResult failure(String draftId, Long revision, String code, String message, String hint) {
        return DraftOperationResult.failure(draftId, revision,
                DraftIssue.error(code, null, null, message, hint));
    }

    private DraftIssue fieldError(String field, String message) {
        return DraftIssue.error("INVALID_FIELD_VALUE", null, field, message, "修正该字段后重试");
    }

    private boolean hasErrors(List<DraftIssue> issues) {
        return issues != null && issues.stream().anyMatch(issue -> "ERROR".equals(issue.level()));
    }

    private List<DraftIssue> mergeIssues(List<DraftIssue> first, List<DraftIssue> second) {
        List<DraftIssue> merged = new ArrayList<>(first == null ? List.of() : first);
        merged.addAll(second == null ? List.of() : second);
        return merged;
    }

    private List<String> changedFields(Map<String, Object> patch, List<String> clearFields) {
        Set<String> fields = new LinkedHashSet<>();
        if (patch != null) {
            fields.addAll(patch.keySet());
        }
        if (clearFields != null) {
            fields.addAll(clearFields);
        }
        return List.copyOf(fields);
    }

    private String requireTextValue(String field, Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(field + " 必须是字符串");
        }
        return blankToNull(text);
    }

    private String normalizeEngine(String engineType) {
        return engineType == null ? null : engineType.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    @FunctionalInterface
    private interface DraftMutation {
        List<DraftIssue> apply(FlowDraft draft);
    }

    @FunctionalInterface
    private interface ResultPayload {
        Map<String, Object> create(FlowDraft draft);
    }
}
