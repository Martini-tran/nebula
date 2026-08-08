package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 精确删除一条草稿边。
 *
 * <p>选择器命中多条边时返回 AMBIGUOUS_EDGE，绝不静默批量删除。
 *
 * @author nebula
 */
public class DisconnectToolDefinition extends AbstractDraftToolDefinition {

    public DisconnectToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "disconnect";
    }

    @Override
    public String name() {
        return "断开草稿边";
    }

    @Override
    public String description() {
        return "按 fromNode+toNode 及可选 sortNo/conditionExpr/eventName 精确删除一条边；歧义时不会删除。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        properties.put("fromNode", stringSchema());
        properties.put("toNode", stringSchema());
        properties.put("sortNo", integerSchema());
        properties.put("conditionExpr", stringSchema());
        properties.put("eventName", stringSchema());
        return objectSchema(properties, "draftId", "expectedRevision", "fromNode", "toNode");
    }

    @Override
    public int sortNo() {
        return 80;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.disconnect(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision"), text(params, "fromNode"), text(params, "toNode"),
                integer(params, "sortNo"), text(params, "conditionExpr"), params.containsKey("conditionExpr"),
                text(params, "eventName"), params.containsKey("eventName")).toToolResponse();
    }
}
