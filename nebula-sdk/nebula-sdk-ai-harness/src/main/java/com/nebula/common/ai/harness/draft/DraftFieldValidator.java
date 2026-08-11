package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.ai.flow.ModelProfileRepository;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.config.HarnessDraftProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B1 双引擎字段级校验器。
 *
 * <p>完整图结构、编译和模拟校验属于 B2；本类保证单次 mutation 不会写入明显不可运行或越界的数据。
 *
 * @author nebula
 */
public class DraftFieldValidator {

    private static final Set<String> STATE_TYPES = Set.of("ENTRY", "NORMAL", "TERMINAL");
    private static final Set<String> OUTPUT_MODES = Set.of("TEXT", "JSON");
    private static final Set<String> ERROR_POLICIES = Set.of("FAIL", "CONTINUE", "GOTO_STATE");

    private final ObjectProvider<ToolRegistry> toolRegistryProvider;
    private final ObjectProvider<AgentDefinitionRepository> agentRepositoryProvider;
    private final ObjectProvider<FlowNodeExecutor> nodeExecutorProvider;
    private final ObjectProvider<ModelProfileRepository> modelProfileRepositoryProvider;
    private final FlowDefinitionCodec codec;
    private final HarnessDraftProperties properties;

    public DraftFieldValidator(ObjectProvider<ToolRegistry> toolRegistryProvider,
                               ObjectProvider<AgentDefinitionRepository> agentRepositoryProvider,
                               ObjectProvider<FlowNodeExecutor> nodeExecutorProvider,
                               ObjectProvider<ModelProfileRepository> modelProfileRepositoryProvider,
                               FlowDefinitionCodec codec,
                               HarnessDraftProperties properties) {
        this.toolRegistryProvider = toolRegistryProvider;
        this.agentRepositoryProvider = agentRepositoryProvider;
        this.nodeExecutorProvider = nodeExecutorProvider;
        this.modelProfileRepositoryProvider = modelProfileRepositoryProvider;
        this.codec = codec;
        this.properties = properties;
    }

    public List<DraftIssue> validate(FlowDraft draft) {
        List<DraftIssue> issues = new ArrayList<>();
        FlowDefinition graph = draft.getGraph();
        List<FlowNodeDefinition> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<FlowEdgeDefinition> edges = graph.getEdges() == null ? List.of() : graph.getEdges();

        if (nodes.size() > properties.getMaxNodes()) {
            issues.add(error("DRAFT_SIZE_LIMIT", null, "nodes", "节点数超过上限 " + properties.getMaxNodes()));
        }
        if (edges.size() > properties.getMaxEdges()) {
            issues.add(error("DRAFT_SIZE_LIMIT", null, "edges", "边数超过上限 " + properties.getMaxEdges()));
        }

        // 流程默认档案：节点未指定 profileCode 时由 FlowGraphFactory 构图期继承此值
        boolean hasDefaultProfile = hasText(graph.getDefaultProfileCode());
        if (hasDefaultProfile) {
            ModelProfileRepository repository = modelProfileRepositoryProvider.getIfAvailable();
            if (repository != null && repository.findByCode(graph.getDefaultProfileCode()) == null) {
                issues.add(error("INVALID_PROFILE_CODE", null, "defaultProfileCode",
                        "流程默认模型档案不存在或已停用: " + graph.getDefaultProfileCode()));
            }
        }

        Set<String> codes = new HashSet<>();
        int entryCount = 0;
        int startCount = 0;
        int endCount = 0;
        for (FlowNodeDefinition node : nodes) {
            issues.addAll(validateNode(draft.getEngineType(), node));
            // 节点与流程都没档案时，运行时会退化到全局兜底模型，行为不可控
            if (LLM_NODE_TYPES.contains(node.getNodeType() == null ? "" : node.getNodeType().toUpperCase())
                    && !hasText(node.getProfileCode()) && !hasDefaultProfile) {
                issues.add(DraftIssue.warn("MISSING_MODEL_PROFILE", node.getNodeCode(), "profileCode",
                        "节点与流程均未指定模型档案，运行时将回退到全局默认模型",
                        "调用 list_model_profiles 后设置节点 profileCode，或用 update_draft_metadata 设置 defaultProfileCode"));
            }
            if (hasText(node.getNodeCode()) && !codes.add(node.getNodeCode())) {
                issues.add(error("DUPLICATE_NODE_CODE", node.getNodeCode(), "nodeCode", "nodeCode 在草稿内必须唯一"));
            }
            if ("ENTRY".equals(node.getStateType())) {
                entryCount++;
            }
            if ("START".equals(node.getNodeType())) {
                startCount++;
            }
            if ("END".equals(node.getNodeType())) {
                endCount++;
            }
        }
        if ("STATE_MACHINE".equals(draft.getEngineType()) && entryCount > 1) {
            issues.add(error("MULTIPLE_ENTRY_STATES", null, "stateType", "状态机草稿只能有一个 ENTRY 状态"));
        }
        if ("DAG".equals(draft.getEngineType()) && (startCount > 1 || endCount > 1)) {
            issues.add(error("DUPLICATE_STRUCTURAL_NODE", null, "nodeType", "DAG 草稿的 START 和 END 各最多一个"));
        }
        for (FlowEdgeDefinition edge : edges) {
            if (!codes.contains(edge.getFromNode()) || !codes.contains(edge.getToNode())) {
                issues.add(error("EDGE_ENDPOINT_NOT_FOUND", null, "edges",
                        "边端点不存在: " + edge.getFromNode() + " -> " + edge.getToNode()));
            }
        }
        return issues;
    }

    public List<DraftIssue> validateNode(String engineType, FlowNodeDefinition node) {
        List<DraftIssue> issues = new ArrayList<>();
        String code = node.getNodeCode();
        String type = node.getNodeType();
        if (!hasText(code)) {
            issues.add(error("MISSING_REQUIRED_FIELD", code, "nodeCode", "节点必须提供 nodeCode"));
        }
        if (!hasText(type)) {
            issues.add(error("MISSING_REQUIRED_FIELD", code, "nodeType", "节点必须提供 nodeType"));
            return issues;
        }
        if (node.getOutputMode() != null && !OUTPUT_MODES.contains(node.getOutputMode())) {
            issues.add(error("INVALID_FIELD_VALUE", code, "outputMode", "outputMode 只接受 TEXT 或 JSON"));
        }
        validateTemplateBudget(code, "systemPrompt", node.getSystemPrompt(), issues);
        validateTemplateBudget(code, "promptTemplate", node.getPromptTemplate(), issues);
        validateModelParams(node, issues);
        if (codec.byteSize(node.getNodeConfig()) > properties.getMaxNodeConfigBytes()) {
            issues.add(error("DRAFT_SIZE_LIMIT", code, "nodeConfig",
                    "nodeConfig 超过 " + properties.getMaxNodeConfigBytes() + " 字节"));
        }

        switch (type) {
            case "PROMPT" -> {
                if (!hasText(node.getPromptTemplate())) {
                    issues.add(error("MISSING_REQUIRED_FIELD", code, "promptTemplate",
                            "nodeType=PROMPT 的节点必须提供 promptTemplate"));
                }
                if (!hasText(node.getOutputKey())) {
                    issues.add(DraftIssue.warn("MISSING_OUTPUT_KEY", code, "outputKey",
                            "PROMPT 节点未设置 outputKey，产物默认以 nodeCode 为键写回",
                            "显式设置一个语义化的 outputKey，便于下游模板引用"));
                }
            }
            case "END" -> {
                // END 不产出结构化结果时，整条流程「跑完了但没有输出」，属于静默失败。
                // 降级为 WARN 而非 ERROR：部分流程确实只靠副作用（写库/发消息）收尾。
                if (!hasText(endOutputTemplate(node))) {
                    issues.add(DraftIssue.warn("MISSING_END_OUTPUT", code, "nodeConfig.end.outputJson",
                            "END 节点未定义 nodeConfig.end.outputJson，流程不会产出结构化结果",
                            "用 {{key}} 引用上游 outputKey 组装最终输出"));
                }
            }
            case "TOOL" -> validateToolNode(node, issues);
            case "AGENT" -> validateAgentNode(node, issues);
            case "AGENT_REACT" -> validateReactNode(node, issues);
            case "LOOP" -> {
                if (asList(node.getNodeConfig().get("members")).isEmpty()) {
                    issues.add(error("MISSING_REQUIRED_FIELD", code, "nodeConfig.members",
                            "LOOP 节点必须提供非空 members"));
                }
            }
            case "START", "IF", "JOIN" -> { }
            default -> {
                if (!registeredNodeType(type)) {
                    issues.add(error("UNKNOWN_NODE_TYPE", code, "nodeType", "未知 nodeType: " + type));
                }
            }
        }

        if ("STATE_MACHINE".equals(engineType)) {
            validateStateNode(node, issues);
        } else if ("START".equals(type) || "END".equals(type)) {
            // DAG 的结构节点合法。
        } else if (hasText(node.getStateType()) || node.getNodeConfig().containsKey("stateConfig")) {
            issues.add(error("ENGINE_FIELD_MISMATCH", code, "stateType",
                    "DAG 节点不能设置状态机字段"));
        }
        return issues;
    }

    @SuppressWarnings("unchecked")
    private void validateStateNode(FlowNodeDefinition node, List<DraftIssue> issues) {
        String code = node.getNodeCode();
        if ("START".equals(node.getNodeType()) || "END".equals(node.getNodeType())) {
            issues.add(error("ENGINE_NODE_TYPE_MISMATCH", code, "nodeType",
                    "STATE_MACHINE 使用 stateType 角色，不能使用 START/END 节点"));
        }
        if (!STATE_TYPES.contains(node.getStateType())) {
            issues.add(error("INVALID_STATE_TYPE", code, "stateType",
                    "stateType 必须严格为 ENTRY、NORMAL 或 TERMINAL"));
        }
        Object raw = node.getNodeConfig().get("stateConfig");
        if (!(raw instanceof Map<?, ?> stateConfig)) {
            return;
        }
        Object retryRaw = stateConfig.get("retry");
        if (retryRaw instanceof Map<?, ?> retry) {
            positiveNumber(code, "maxAttempts", retry.get("maxAttempts"), issues);
            nonNegativeNumber(code, "backoffMs", retry.get("backoffMs"), issues);
            listOfText(code, "retryOn", retry.get("on"), issues);
        }
        positiveNumber(code, "stateTimeoutMs", stateConfig.get("timeoutMs"), issues);
        String onError = stateConfig.get("onError") == null ? null : String.valueOf(stateConfig.get("onError"));
        if (onError != null && !ERROR_POLICIES.contains(onError)) {
            issues.add(error("INVALID_FIELD_VALUE", code, "onError",
                    "onError 只接受 FAIL、CONTINUE 或 GOTO_STATE"));
        }
        Object errorStateValue = stateConfig.get("errorState");
        String errorState = errorStateValue instanceof String text ? text : null;
        if (errorStateValue != null && errorState == null) {
            issues.add(error("INVALID_FIELD_VALUE", code, "errorState", "errorState 必须是字符串"));
        }
        if ("GOTO_STATE".equals(onError) && !hasText(errorState)) {
            issues.add(error("MISSING_REQUIRED_FIELD", code, "errorState",
                    "onError=GOTO_STATE 时必须提供 errorState"));
        }
        listOfText(code, "awaitingEvents", stateConfig.get("awaitingEvents"), issues);
    }

    private void validateToolNode(FlowNodeDefinition node, List<DraftIssue> issues) {
        Object codeValue = node.getNodeConfig().get("toolCode");
        String toolCode = codeValue == null ? null : String.valueOf(codeValue);
        ToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
        ToolDefinition tool = hasText(toolCode) && toolRegistry != null ? toolRegistry.find(toolCode) : null;
        if (tool == null || tool.invocationScopes() == null
                || !tool.invocationScopes().contains(InvocationScope.FLOW_NODE)) {
            issues.add(error("INVALID_TOOL_CODE", node.getNodeCode(), "nodeConfig.toolCode",
                    "TOOL 节点必须引用存在且允许 FLOW_NODE 调用的工具"));
        }
    }

    private void validateAgentNode(FlowNodeDefinition node, List<DraftIssue> issues) {
        Object value = node.getNodeConfig().get("refAgentCode");
        String agentCode = value == null ? null : String.valueOf(value);
        AgentDefinitionRepository agentRepository = agentRepositoryProvider.getIfAvailable();
        if (!hasText(agentCode) || agentRepository == null || agentRepository.find(agentCode) == null) {
            issues.add(error("INVALID_AGENT_CODE", node.getNodeCode(), "nodeConfig.refAgentCode",
                    "AGENT 节点必须引用已定义的 refAgentCode"));
        }
    }

    /** 会真实调用模型、因而需要模型参数的节点类型。 */
    private static final Set<String> LLM_NODE_TYPES = Set.of("PROMPT", "AGENT_REACT");

    /**
     * 校验模型档案与采样参数。
     *
     * <p>此前 profileCode 完全不校验：模型可以凭空写一个 "gpt-4"，写入成功、校验通过、
     * 直到真跑时 ModelProfileRepository 返回 null 才静默退化成全局默认模型——
     * 表现为「配了档案但没生效」，极难排查。这里在写入期就挡住。
     *
     * <p>采样参数越界同理：temperature=5 各厂商行为不一（截断或直接 400），
     * 与其等运行时报错，不如在建图期给出明确范围。
     */
    private void validateModelParams(FlowNodeDefinition node, List<DraftIssue> issues) {
        String code = node.getNodeCode();
        if (hasText(node.getProfileCode())) {
            ModelProfileRepository repository = modelProfileRepositoryProvider.getIfAvailable();
            // 仓储缺失时不误报：SDK 允许降级为内存实现，此处无法判定真伪
            if (repository != null && repository.findByCode(node.getProfileCode()) == null) {
                issues.add(error("INVALID_PROFILE_CODE", code, "profileCode",
                        "模型档案不存在或已停用: " + node.getProfileCode()));
            }
        }
        // 采样参数范围：取各主流厂商的公共安全区间
        if (node.getTemperature() != null && (node.getTemperature() < 0 || node.getTemperature() > 2)) {
            issues.add(error("INVALID_FIELD_VALUE", code, "temperature", "temperature 取值范围为 0~2"));
        }
        if (node.getTopP() != null && (node.getTopP() < 0 || node.getTopP() > 1)) {
            issues.add(error("INVALID_FIELD_VALUE", code, "topP", "topP 取值范围为 0~1"));
        }
        if (node.getMaxTokens() != null && node.getMaxTokens() <= 0) {
            issues.add(error("INVALID_FIELD_VALUE", code, "maxTokens", "maxTokens 必须为正整数"));
        }
        if (node.getTimeoutMs() != null && node.getTimeoutMs() <= 0) {
            issues.add(error("INVALID_FIELD_VALUE", code, "timeoutMs", "timeoutMs 必须为正整数"));
        }
        // JSON 产出模式若提示词未要求输出 JSON，解析大概率失败，产物无法被下游结构化消费
        if (LLM_NODE_TYPES.contains(node.getNodeType() == null ? "" : node.getNodeType().toUpperCase())
                && "JSON".equalsIgnoreCase(node.getOutputMode())
                && !mentionsJson(node.getPromptTemplate()) && !mentionsJson(node.getSystemPrompt())) {
            issues.add(DraftIssue.warn("JSON_MODE_WITHOUT_INSTRUCTION", code, "promptTemplate",
                    "outputMode=JSON 但提示词未要求模型输出 JSON，解析可能失败",
                    "在提示词中明确「只输出 JSON」并给出字段结构"));
        }
    }

    private boolean mentionsJson(String template) {
        return template != null && template.toLowerCase().contains("json");
    }

    /** 取 nodeConfig.end.outputJson，缺失或结构不符时返回 null。 */
    @SuppressWarnings("unchecked")
    private String endOutputTemplate(FlowNodeDefinition node) {
        Object end = node.getNodeConfig() == null ? null : node.getNodeConfig().get("end");
        if (!(end instanceof Map<?, ?> map)) {
            return null;
        }
        Object template = ((Map<String, Object>) map).get("outputJson");
        return template == null ? null : String.valueOf(template);
    }

    private void validateReactNode(FlowNodeDefinition node, List<DraftIssue> issues) {
        // AGENT_REACT 与 PROMPT 一样渲染 promptTemplate 并调模型（见 AgentReactNodeExecutor），
        // 此前只校验 toolCodes，导致「有工具但没指令」的空壳 ReAct 节点能通过校验。
        if (!hasText(node.getPromptTemplate())) {
            issues.add(error("MISSING_REQUIRED_FIELD", node.getNodeCode(), "promptTemplate",
                    "nodeType=AGENT_REACT 的节点必须提供 promptTemplate 说明任务目标"));
        }
        List<?> codes = asList(node.getNodeConfig().get("toolCodes"));
        if (codes.isEmpty()) {
            issues.add(error("MISSING_REQUIRED_FIELD", node.getNodeCode(), "nodeConfig.toolCodes",
                    "AGENT_REACT 节点必须提供非空 toolCodes"));
            return;
        }
        ToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
        for (Object value : codes) {
            String toolCode = value == null ? null : String.valueOf(value);
            ToolDefinition tool = hasText(toolCode) && toolRegistry != null ? toolRegistry.find(toolCode) : null;
            if (tool == null || tool.invocationScopes() == null
                    || !tool.invocationScopes().contains(InvocationScope.FLOW_NODE)) {
                issues.add(error("INVALID_TOOL_CODE", node.getNodeCode(), "nodeConfig.toolCodes",
                        "AGENT_REACT 引用了不存在或不可供流程调用的工具: " + toolCode));
            }
        }
    }

    private void validateTemplateBudget(String code, String field, String value, List<DraftIssue> issues) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > properties.getMaxTemplateBytes()) {
            issues.add(error("DRAFT_SIZE_LIMIT", code, field,
                    field + " 超过 " + properties.getMaxTemplateBytes() + " 字节"));
        }
    }

    private void positiveNumber(String code, String field, Object value, List<DraftIssue> issues) {
        if (value != null && (!(value instanceof Number number) || number.longValue() <= 0)) {
            issues.add(error("INVALID_FIELD_VALUE", code, field, field + " 必须为正整数"));
        }
    }

    private void nonNegativeNumber(String code, String field, Object value, List<DraftIssue> issues) {
        if (value != null && (!(value instanceof Number number) || number.longValue() < 0)) {
            issues.add(error("INVALID_FIELD_VALUE", code, field, field + " 必须为非负整数"));
        }
    }

    private void listOfText(String code, String field, Object value, List<DraftIssue> issues) {
        if (value != null && (!(value instanceof List<?> list)
                || list.stream().anyMatch(item -> !(item instanceof String text) || text.isBlank()))) {
            issues.add(error("INVALID_FIELD_VALUE", code, field, field + " 必须是非空字符串数组"));
        }
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private DraftIssue error(String code, String nodeCode, String field, String message) {
        return DraftIssue.error(code, nodeCode, field, message, "修正该字段后重试当前 mutation");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean registeredNodeType(String type) {
        return nodeExecutorProvider.orderedStream().anyMatch(executor -> type.equals(executor.type()));
    }
}
