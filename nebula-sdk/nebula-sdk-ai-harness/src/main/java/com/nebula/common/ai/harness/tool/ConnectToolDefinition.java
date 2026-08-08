package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在两个草稿节点之间创建一条边。
 *
 * @author nebula
 */
public class ConnectToolDefinition extends AbstractDraftToolDefinition {

    public ConnectToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "connect";
    }

    @Override
    public String name() {
        return "连接草稿节点";
    }

    @Override
    public String description() {
        return "创建一条有向边，可附带 SpEL conditionExpr、状态机 eventName 和裁决 sortNo。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        properties.put("fromNode", stringSchema());
        properties.put("toNode", stringSchema());
        properties.put("conditionExpr", stringSchema());
        properties.put("eventName", stringSchema());
        properties.put("sortNo", integerSchema());
        return objectSchema(properties, "draftId", "expectedRevision", "fromNode", "toNode");
    }

    @Override
    public int sortNo() {
        return 70;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.connect(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision"), text(params, "fromNode"), text(params, "toNode"),
                text(params, "conditionExpr"), text(params, "eventName"), integer(params, "sortNo"))
                .toToolResponse();
    }
}
