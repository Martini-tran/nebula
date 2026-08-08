package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.InvocationScope;
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
    private final FlowDefinitionCodec codec;
    private final HarnessDraftProperties properties;

    public DraftFieldValidator(ObjectProvider<ToolRegistry> toolRegistryProvider,
                               ObjectProvider<AgentDefinitionRepository> agentRepositoryProvider,
                               ObjectProvider<FlowNodeExecutor> nodeExecutorProvider,
                               FlowDefinitionCodec codec,
                               HarnessDraftProperties properties) {
        this.toolRegistryProvider = toolRegistryProvider;
        this.agentRepositoryProvider = agentRepositoryProvider;
        this.nodeExecutorProvider = nodeExecutorProvider;
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

        Set<String> codes = new HashSet<>();
        int entryCount = 0;
        int startCount = 0;
        int endCount = 0;
        for (FlowNodeDefinition node : nodes) {
            issues.addAll(validateNode(draft.getEngineType(), node));
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
                            "PROMPT 节点未设置 outputKey，产物不会写回上下文", "设置一个稳定的 outputKey"));
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
            case "START", "END", "IF", "JOIN" -> { }
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

    private void validateReactNode(FlowNodeDefinition node, List<DraftIssue> issues) {
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
