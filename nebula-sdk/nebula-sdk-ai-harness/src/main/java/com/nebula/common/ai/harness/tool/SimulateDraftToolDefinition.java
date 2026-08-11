package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/** 在草稿深拷贝上执行零副作用双引擎模拟。 */
public class SimulateDraftToolDefinition extends AbstractDraftToolDefinition {

    public SimulateDraftToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "simulate_draft";
    }

    @Override
    public String name() {
        return "模拟流程草稿";
    }

    @Override
    public String description() {
        return "重新校验指定 revision，并用根据入口节点输入契约生成的 initialInput 做零 token、零外部副作用模拟；"
                + "无声明入参时显式传空对象，报告明确标记 guard 假设。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        properties.put("initialInput", Map.of(
                "type", "object",
                "description", "根据 START/ENTRY 节点 nodeConfig.inputs 生成的测试参数；无入参时传空对象",
                "additionalProperties", true));
        return objectSchema(properties, "draftId", "expectedRevision", "initialInput");
    }

    @Override
    public int sortNo() {
        return 105;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.simulate(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision"), map(params, "initialInput")).toToolResponse();
    }
}
