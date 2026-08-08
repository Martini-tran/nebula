package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 以 patch + clearFields 更新单个节点。
 *
 * @author nebula
 */
public class UpdateNodeToolDefinition extends AbstractDraftToolDefinition {

    public UpdateNodeToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "update_node";
    }

    @Override
    public String name() {
        return "更新草稿节点";
    }

    @Override
    public String description() {
        return "更新一个节点的指定字段。nodeCode 不可改名；显式清空字段必须使用 clearFields。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        properties.put("nodeCode", stringSchema());
        properties.put("patch", NodeToolSchemas.patchSchema());
        properties.put("clearFields", stringArraySchema());
        return objectSchema(properties, "draftId", "expectedRevision", "nodeCode", "patch");
    }

    @Override
    public int sortNo() {
        return 50;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.updateNode(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision"), text(params, "nodeCode"), map(params, "patch"),
                strings(params, "clearFields")).toToolResponse();
    }
}
