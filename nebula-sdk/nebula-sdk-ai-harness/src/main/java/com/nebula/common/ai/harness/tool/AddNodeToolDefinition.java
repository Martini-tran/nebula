package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向草稿添加一个节点，状态机治理字段使用扁平参数。
 *
 * @author nebula
 */
public class AddNodeToolDefinition extends AbstractDraftToolDefinition {

    public AddNodeToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "add_node";
    }

    @Override
    public String name() {
        return "添加草稿节点";
    }

    @Override
    public String description() {
        return "添加单个节点。状态机字段保持扁平，服务端会转换到 stateType 与 nodeConfig.stateConfig。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        properties.putAll(NodeToolSchemas.fields());
        return objectSchema(properties, "draftId", "expectedRevision", "nodeCode", "nodeType");
    }

    @Override
    public int sortNo() {
        return 40;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.addNode(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision"), without(params, "draftId", "expectedRevision"))
                .toToolResponse();
    }
}
