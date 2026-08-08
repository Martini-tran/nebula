package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;
import com.nebula.common.ai.harness.draft.EngineTypeCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建空流程草稿。
 *
 * @author nebula
 */
public class CreateDraftToolDefinition extends AbstractDraftToolDefinition {

    public CreateDraftToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "create_draft";
    }

    @Override
    public String name() {
        return "创建流程草稿";
    }

    @Override
    public String description() {
        return "创建空草稿。engineType 必填且创建后不可修改；flowCode 可空，提交前再补齐。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("engineType", Map.of("type", "string",
                "enum", List.of(EngineTypeCatalog.DAG, EngineTypeCatalog.STATE_MACHINE)));
        properties.put("flowCode", stringSchema());
        properties.put("name", stringSchema());
        properties.put("description", stringSchema());
        return objectSchema(properties, "engineType");
    }

    @Override
    public int sortNo() {
        return 20;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.create(DraftAccess.from(ctx), text(params, "engineType"), text(params, "flowCode"),
                text(params, "name"), text(params, "description")).toToolResponse();
    }
}
