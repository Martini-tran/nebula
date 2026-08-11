package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询某节点可引用的上下文变量，供写 promptTemplate 前自查。
 *
 * @author nebula
 */
public class InspectContextToolDefinition extends AbstractDraftToolDefinition {

    public InspectContextToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "inspect_context";
    }

    @Override
    public String name() {
        return "查询节点可用上下文";
    }

    @Override
    public String description() {
        return "返回该节点可在 promptTemplate/systemPrompt 中引用的变量："
                + "startInputs 为流程入参，guaranteed 为必然可用，conditional 仅部分分支可用，"
                + "referenced 为当前模板已引用的变量。写模板前调用可避免变量名猜错。";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", stringSchema());
        properties.put("nodeCode", stringSchema());
        return objectSchema(properties, "draftId", "nodeCode");
    }

    @Override
    public int sortNo() {
        return 95;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.inspectContext(DraftAccess.from(ctx), text(params, "draftId"), text(params, "nodeCode"))
                .toToolResponse();
    }
}
