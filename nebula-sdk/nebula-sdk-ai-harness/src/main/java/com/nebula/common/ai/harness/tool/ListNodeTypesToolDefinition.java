package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.harness.draft.EngineTypeCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按执行内核列出权威节点类型、图语义角色及建图约束。
 *
 * <p>执行节点来自容器内实际注册的 executor；START/END 等结构角色按引擎分组，避免把 DAG 角色误用于状态机。
 *
 * @author nebula
 */
@RequiredArgsConstructor
public class ListNodeTypesToolDefinition implements ToolDefinition {

    private static final Set<String> DAG_ROLES = Set.of("START", "END", "IF", "JOIN", "LOOP");

    private static final Map<String, String> SEMANTICS = semantics();

    private final ObjectProvider<FlowNodeExecutor> executorProvider;

    @Override
    public Set<InvocationScope> invocationScopes() {
        return Set.of(InvocationScope.COPILOT_TOOL);
    }

    @Override
    public String code() {
        return "list_node_types";
    }

    @Override
    public String name() {
        return "列出双引擎节点类型";
    }

    @Override
    public String description() {
        return "按 DAG 或 STATE_MACHINE 返回权威执行节点类型、图语义角色和约束；不传 engineType 时返回两组供选型。";
    }

    @Override
    public String category() {
        return "copilot";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("engineType", Map.of(
                        "type", "string", "enum", List.of(EngineTypeCatalog.DAG, EngineTypeCatalog.STATE_MACHINE))),
                "additionalProperties", false);
    }

    @Override
    public int sortNo() {
        return 10;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        String filter = params.get("engineType") == null ? null
                : String.valueOf(params.get("engineType")).toUpperCase();
        Map<String, Object> engines = new LinkedHashMap<>();
        if (filter == null || EngineTypeCatalog.DAG.equals(filter)) {
            engines.put(EngineTypeCatalog.DAG, describeDag());
        }
        if (filter == null || EngineTypeCatalog.STATE_MACHINE.equals(filter)) {
            engines.put(EngineTypeCatalog.STATE_MACHINE, describeStateMachine());
        }
        return Map.of("ok", true, "engines", engines,
                "selectionHint", "一次性向前推进选择 DAG；需要回跳、重试、审批打回或多轮收敛选择 STATE_MACHINE");
    }

    private Map<String, Object> describeDag() {
        return Map.of(
                "executionNodeTypes", executionTypes(),
                "graphRoles", List.of(
                        role("START", "唯一入口，可在 nodeConfig.inputs 声明输入"),
                        role("END", "唯一出口，可在 nodeConfig.end.outputJson 定义最终产物"),
                        role("IF", "条件分支，条件写在出边 conditionExpr"),
                        role("JOIN", "并行分支汇聚点"),
                        role("LOOP", "FOREACH/COUNT 循环容器，成员写在 nodeConfig.members")),
                "constraints", EngineTypeCatalog.constraints(EngineTypeCatalog.DAG));
    }

    private Map<String, Object> describeStateMachine() {
        return Map.of(
                "executionNodeTypes", executionTypes(),
                "graphRoles", List.of(
                        role("ENTRY", "stateType=ENTRY，唯一入口态"),
                        role("NORMAL", "stateType=NORMAL，普通状态"),
                        role("TERMINAL", "stateType=TERMINAL，终态且不得有出边")),
                "stateFields", List.of("stateType", "maxAttempts", "backoffMs", "retryOn",
                        "stateTimeoutMs", "onError", "errorState", "suspend", "awaitingEvents"),
                "constraints", EngineTypeCatalog.constraints(EngineTypeCatalog.STATE_MACHINE));
    }

    private List<Map<String, Object>> executionTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        executorProvider.orderedStream()
                .map(FlowNodeExecutor::type)
                .filter(type -> type != null && !type.isBlank() && !DAG_ROLES.contains(type))
                .distinct()
                .sorted()
                .forEach(type -> types.add(role(type, SEMANTICS.getOrDefault(type, type))));
        return types;
    }

    private Map<String, Object> role(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, String> semantics() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("PROMPT", "渲染 promptTemplate 并调用模型，产物写入 outputKey");
        values.put("TOOL", "调用 nodeConfig.toolCode 指定的 FLOW_NODE 工具");
        values.put("AGENT", "调用 nodeConfig.refAgentCode 指定的子 Agent");
        values.put("AGENT_REACT", "让模型在 nodeConfig.toolCodes 白名单内多轮选调工具");
        return values;
    }
}
