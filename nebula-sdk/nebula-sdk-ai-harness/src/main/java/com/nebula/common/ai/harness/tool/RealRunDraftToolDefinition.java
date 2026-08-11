package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.harness.conversation.HarnessToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.realrun.DraftRealRunService;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 双阶段确认后真实执行当前草稿 revision。 */
@RequiredArgsConstructor
public class RealRunDraftToolDefinition implements ToolDefinition {

    private final DraftRealRunService service;

    @Override
    public String code() {
        return "real_run_draft";
    }

    @Override
    public String name() {
        return "真实试跑流程草稿";
    }

    @Override
    public String description() {
        return "真实调用模型、工具和子 Agent 验收当前 revision；首次执行及失败重试都先请求用户确认，"
                + "initialInput 必须根据入口节点输入契约生成，同一 revision 可持续重试直到成功。";
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
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", Map.of("type", "string"));
        properties.put("expectedRevision", Map.of("type", "integer"));
        properties.put("initialInput", Map.of(
                "type", "object",
                "description", "根据 START/ENTRY 节点 nodeConfig.inputs 生成的测试参数；无入参时传空对象",
                "additionalProperties", true));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", java.util.List.of("draftId", "expectedRevision", "initialInput"),
                "additionalProperties", false);
    }

    @Override
    public int sortNo() {
        return 110;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Map<String, Object> params, ToolContext context) {
        Map<String, Object> input = params.get("initialInput") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        String token = context == null ? null
                : context.getString(HarnessToolContext.CONFIRMATION_TOKEN_ATTRIBUTE);
        return service.realRun(
                DraftAccess.from(context),
                String.valueOf(params.get("draftId")),
                ((Number) params.get("expectedRevision")).longValue(),
                input,
                token).toResponse();
    }
}
