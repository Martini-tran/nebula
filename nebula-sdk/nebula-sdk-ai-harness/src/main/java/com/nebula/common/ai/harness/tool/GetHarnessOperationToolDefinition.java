package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.realrun.DraftRealRunService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 查询真实试跑等高风险 operation 的持久化状态。 */
@RequiredArgsConstructor
public class GetHarnessOperationToolDefinition implements ToolDefinition {

    private final DraftRealRunService service;

    @Override
    public String code() {
        return "get_harness_operation";
    }

    @Override
    public String name() {
        return "查询 Harness 操作";
    }

    @Override
    public String description() {
        return "按 operationId 查询 PENDING/RUNNING/SUCCEEDED/FAILED/UNKNOWN 状态和安全结果摘要。";
    }

    @Override
    public String category() {
        return "copilot";
    }

    @Override
    public Set<InvocationScope> invocationScopes() {
        return Set.of(InvocationScope.COPILOT_TOOL);
    }

    @Override
    public Map<String, Object> paramsSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("operationId", Map.of("type", "string")),
                "required", List.of("operationId"),
                "additionalProperties", false);
    }

    @Override
    public int sortNo() {
        return 115;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext context) {
        return service.getOperation(
                DraftAccess.from(context), String.valueOf(params.get("operationId"))).toResponse();
    }
}
