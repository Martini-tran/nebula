package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftApplicationService;

import java.util.LinkedHashMap;
import java.util.Map;

/** 重新校验并以 CREATE_ONLY 语义原子提交草稿。 */
public class CommitDraftToolDefinition extends AbstractDraftToolDefinition {

    public CommitDraftToolDefinition(DraftApplicationService service) {
        super(service);
    }

    @Override
    public String code() {
        return "commit_draft";
    }

    @Override
    public String name() {
        return "提交流程草稿";
    }

    @Override
    public String description() {
        return "重新校验当前 revision 并原子创建正式流程；flowCode 已存在时返回冲突，绝不覆盖。";
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
        return 110;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        return service.commit(DraftAccess.from(ctx), text(params, "draftId"),
                longValue(params, "expectedRevision")).toToolResponse();
    }
}
