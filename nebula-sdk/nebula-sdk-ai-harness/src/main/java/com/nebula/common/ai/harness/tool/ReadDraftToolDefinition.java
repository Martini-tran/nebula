package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 有界读取草稿摘要、指定节点详情和分页边。
 *
 * @author nebula
 */
public class ReadDraftToolDefinition extends AbstractDraftToolDefinition {

    public ReadDraftToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "read_draft";
    }

    @Override
    public String name() {
        return "读取流程草稿";
    }

    @Override
    public String description() {
        return "默认读取全图索引摘要；传 nodeCodes 获取节点详情，边始终按 edgePage/edgePageSize 分页。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("nodeCodes", stringArraySchema());
        properties.put("edgePage", integerSchema());
        properties.put("edgePageSize", integerSchema());
        return objectSchema(properties, "draftId");
    }

    @Override
    public int sortNo() {
        return 90;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.read(DraftAccess.from(ctx), text(params, "draftId"), strings(params, "nodeCodes"),
                integer(params, "edgePage"), integer(params, "edgePageSize")).toToolResponse();
    }
}
