package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/** 对当前草稿 revision 执行完整的双引擎校验。 */
public class ValidateDraftToolDefinition extends AbstractDraftToolDefinition {

    public ValidateDraftToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "validate_draft";
    }

    @Override
    public String name() {
        return "校验流程草稿";
    }

    @Override
    public String description() {
        return "校验指定 revision 的结构、数据流、条件语法和引擎编译结果；ERROR 阻断提交，WARN 不阻断。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("expectedRevision", integerSchema());
        return objectSchema(properties, "draftId", "expectedRevision");
    }

    @Override
    public int sortNo() {
        return 100;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.validate(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision")).toToolResponse();
    }
}
