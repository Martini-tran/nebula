package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 以 patch + clearFields 更新草稿元数据。
 *
 * @author nebula
 */
public class UpdateDraftMetadataToolDefinition extends AbstractDraftToolDefinition {

    public UpdateDraftMetadataToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "update_draft_metadata";
    }

    @Override
    public String name() {
        return "更新草稿元数据";
    }

    @Override
    public String description() {
        return "更新 name、description、flowCode、defaultProfileCode 或 maxTransitions；engineType 不可修改。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        properties.put("patch", Map.of("type", "object"));
        properties.put("clearFields", stringArraySchema());
        return objectSchema(properties, "draftId", "expectedRevision", "patch");
    }

    @Override
    public int sortNo() {
        return 30;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.updateMetadata(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision"), map(params, "patch"), strings(params, "clearFields"))
                .toToolResponse();
    }
}
