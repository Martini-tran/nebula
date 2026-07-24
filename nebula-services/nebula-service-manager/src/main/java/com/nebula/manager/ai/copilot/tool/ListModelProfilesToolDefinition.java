package com.nebula.manager.ai.copilot.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.ai.profile.ModelProfileAdminService;
import com.nebula.manager.dto.ModelProfilePageQuery;
import com.nebula.manager.vo.ModelProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot 工具：列出可用的模型档案（ai_model_profile）。
 * 供"流程设计助手"为流程/节点选择合法的 profileCode（默认模型档案）。
 * 仅返回启用档案的非敏感字段，绝不返回 apiKey（Service 出参本就掩码）。
 *
 * @author nebula
 */
@Component
@RequiredArgsConstructor
public class ListModelProfilesToolDefinition implements ToolDefinition {

    /**
     * 一次性取全量的分页上限（模型档案数量有限，避免分页往返）。
     */
    private static final int MAX_PROFILES = 500;

    private final ModelProfileAdminService modelProfileAdminService;

    @Override
    public String code() {
        return "list_model_profiles";
    }

    @Override
    public String name() {
        return "列出模型档案";
    }

    @Override
    public String description() {
        return "列出可用的模型档案（profileCode + 名称 + provider + model）。为流程或节点选择 defaultProfileCode/profileCode 时先调用本工具获取合法编码。";
    }

    @Override
    public String category() {
        return ListNodeTypesToolDefinition.COPILOT_CATEGORY;
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    @Override
    public int sortNo() {
        return 12;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        ModelProfilePageQuery query = new ModelProfilePageQuery();
        query.setPageNum(1);
        query.setPageSize(MAX_PROFILES);
        query.setStatus(1);
        PageResult<ModelProfileVO> page = modelProfileAdminService.page(query);

        List<Map<String, Object>> profiles = new ArrayList<>();
        if (page != null && page.getRecords() != null) {
            for (ModelProfileVO vo : page.getRecords()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("profileCode", vo.getProfileCode());
                one.put("name", vo.getName());
                one.put("provider", vo.getProvider());
                one.put("model", vo.getModel());
                profiles.add(one);
            }
        }
        return profiles;
    }
}
