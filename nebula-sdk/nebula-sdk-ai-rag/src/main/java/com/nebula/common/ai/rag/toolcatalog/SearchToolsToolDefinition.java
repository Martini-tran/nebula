package com.nebula.common.ai.rag.toolcatalog;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具语义检索工具（{@code search_tools}）。
 *
 * <p>与 {@code knowledge_search} 同构，挂在既有 {@code ToolRegistry} seam 上：注册为 Bean 即被
 * {@code ToolRegistrySynchronizer} 镜像进 {@code ai_tool} 表，Copilot / Agent 主循环可调用。
 * {@code invoke = ToolCatalogService.search(query) → 命中 code 回填 ToolRegistry 里的 name/description/paramsSchema/category}。
 *
 * <p><b>为何需要它</b>：工具数量增长后，把全部工具塞进 system prompt 既费 token 又稀释注意力。让模型先
 * {@code search_tools("发邮件")} 语义召回相关工具、再按需调用，是「工具太多」时的可扩展检索层。
 *
 * <p><b>可用性铁律</b>：任何检索异常都 catch 成 {@code {ok:false,error}} map 返回，绝不抛进 Copilot / Agent 主循环。
 *
 * @author nebula
 */
@Slf4j
public class SearchToolsToolDefinition implements ToolDefinition {

    /**
     * 默认返回条数
     */
    private static final int DEFAULT_TOP_K = 5;

    private final ToolCatalogService toolCatalogService;

    /**
     * 惰性取 {@link ToolRegistry} 回填命中工具的元数据。
     *
     * <p><b>为何惰性</b>：本工具自身是一个 {@link ToolDefinition}，而 {@link ToolRegistry} 由全部 {@link ToolDefinition}
     * 构造——若构造期直接注入 {@link ToolRegistry} 会形成「search_tools → ToolRegistry → search_tools」循环依赖。
     * 用 {@link ObjectProvider} 把取用推迟到 {@link #invoke} 运行期，构造期不触发 {@link ToolRegistry} 实例化，打破循环。
     */
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;

    public SearchToolsToolDefinition(ToolCatalogService toolCatalogService,
                                     ObjectProvider<ToolRegistry> toolRegistryProvider) {
        this.toolCatalogService = toolCatalogService;
        this.toolRegistryProvider = toolRegistryProvider;
    }

    @Override
    public String code() {
        return "search_tools";
    }

    @Override
    public String name() {
        return "工具语义检索";
    }

    @Override
    public String description() {
        return "按自然语言意图语义检索可用工具，返回最相关的 top-k 工具（含编码/名称/描述/入参 schema）。"
                + "工具数量多时，先用它找到相关工具再按需调用，避免全量列举。";
    }

    @Override
    public String category() {
        return "search";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "查询意图（要完成的任务 / 想找的工具能力）");

        Map<String, Object> topK = new LinkedHashMap<>();
        topK.put("type", "integer");
        topK.put("description", "返回条数，默认 5");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", query);
        properties.put("topK", topK);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("query"));
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        try {
            String query = str(params.get("query"));
            int topK = intOf(params.get("topK"), DEFAULT_TOP_K);
            if (query == null || query.isBlank()) {
                return error("query 必填");
            }
            List<String> codes = toolCatalogService.search(query, topK);
            ToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
            List<Map<String, Object>> hits = new ArrayList<>(codes.size());
            for (String code : codes) {
                // 命中的 search_tools 自身不回给模型（避免自指）
                if (code().equals(code)) {
                    continue;
                }
                ToolDefinition tool = toolRegistry == null ? null : toolRegistry.find(code);
                Map<String, Object> hit = new LinkedHashMap<>();
                if (tool != null) {
                    // 回填注册表里的完整元数据
                    hit.put("code", tool.code());
                    hit.put("name", tool.name());
                    hit.put("description", tool.description());
                    hit.put("category", tool.category());
                    hit.put("paramsSchema", tool.paramsSchema());
                } else {
                    // 注册表不可用或该 code 已下线：至少回 code，模型仍可据此决定是否调用
                    hit.put("code", code);
                }
                hits.add(hit);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("tools", hits);
            return result;
        } catch (Exception e) {
            log.warn("search_tools 执行失败（降级返回 error map）：{}", e.getMessage());
            return error(e.getMessage());
        }
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("error", message);
        return result;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
