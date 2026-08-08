package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 删除节点并报告随之清理的关联边。
 *
 * @author nebula
 */
public class RemoveNodeToolDefinition extends AbstractDraftToolDefinition {

    public RemoveNodeToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "remove_node";
    }

    @Override
    public String name() {
        return "删除草稿节点";
    }

    @Override
    public String description() {
        return "删除一个节点并自动清理全部关联边，响应中返回被清理边的摘要。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        properties.put("nodeCode", stringSchema());
        return objectSchema(properties, "draftId", "expectedRevision", "nodeCode");
    }

    @Override
    public int sortNo() {
        return 60;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.removeNode(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision"), text(params, "nodeCode")).toToolResponse();
    }
}
