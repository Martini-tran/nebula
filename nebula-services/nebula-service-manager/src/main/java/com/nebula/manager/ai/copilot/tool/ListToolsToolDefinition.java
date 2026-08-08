package com.nebula.manager.ai.copilot.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.ai.tool.ToolAdminService;
import com.nebula.manager.dto.ToolPageQuery;
import com.nebula.manager.vo.ToolVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copilot 工具：列出可在 TOOL 节点引用的已启用业务工具。
 * 供"流程设计助手"在需要往流程里放 TOOL 节点时，查得合法的 toolCode 与入参 schema。
 * 排除 copilot 自身工具（category=copilot），避免模型把生成类工具当业务工具塞进流程。
 *
 * @author nebula
 */
@Component
@RequiredArgsConstructor
public class ListToolsToolDefinition implements ToolDefinition {

    @Override
    public Set<InvocationScope> invocationScopes() {
        return Set.of(InvocationScope.COPILOT_TOOL);
    }

    /**
     * 一次性取全量的分页上限（业务工具数量有限，避免分页往返）。
     */
    private static final int MAX_TOOLS = 500;

    private final ToolAdminService toolAdminService;

    @Override
    public String code() {
        return "list_tools";
    }

    @Override
    public String name() {
        return "列出可用业务工具";
    }

    @Override
    public String description() {
        return "列出可在 TOOL 节点引用的已启用业务工具（toolCode + 用途 + 入参 schema）。当流程需要调用工具时先调用本工具获取合法 toolCode。";
    }

    @Override
    public String category() {
        return CopilotToolSupport.CATEGORY;
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "可选：按工具编码/名称/描述过滤的关键词");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", keyword);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public int sortNo() {
        return 11;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        ToolPageQuery query = new ToolPageQuery();
        query.setPageNum(1);
        query.setPageSize(MAX_TOOLS);
        query.setEnabled(1);
        if (params != null && params.get("keyword") != null) {
            query.setKeyword(String.valueOf(params.get("keyword")));
        }
        PageResult<ToolVO> page = toolAdminService.page(query);

        List<Map<String, Object>> tools = new ArrayList<>();
        if (page != null && page.getRecords() != null) {
            for (ToolVO vo : page.getRecords()) {
                // 排除 copilot 自身工具，只暴露业务工具供流程 TOOL 节点引用
                if (CopilotToolSupport.CATEGORY.equals(vo.getCategory())) {
                    continue;
                }
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("toolCode", vo.getToolCode());
                one.put("name", vo.getName());
                one.put("description", vo.getDescription());
                one.put("category", vo.getCategory());
                one.put("paramsSchema", vo.getParamsSchema());
                tools.add(one);
            }
        }
        return tools;
    }
}
