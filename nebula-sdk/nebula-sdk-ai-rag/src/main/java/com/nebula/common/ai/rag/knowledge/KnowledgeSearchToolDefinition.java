package com.nebula.common.ai.rag.knowledge;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.rag.VectorMatch;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具（{@code knowledge_search}）
 * 挂在既有 {@code ToolRegistry} seam 上：注册为 Bean 即被 {@code ToolRegistrySynchronizer} 镜像进 {@code ai_tool} 表，
 * 流程 TOOL / AGENT_REACT 节点与 Flow Copilot 均可调用。{@code invoke = embed(query) → VectorStore.search → 返回
 * [{content, score, metadata}]}。
 *
 * <p><b>可用性铁律</b>：任何检索异常都 catch 成 {@code {ok:false,error}} map 返回，绝不抛进 Copilot / Agent 主循环。
 *
 * @author nebula
 */
@Slf4j
public class KnowledgeSearchToolDefinition implements ToolDefinition {

    /**
     * 默认返回条数
     */
    private static final int DEFAULT_TOP_K = 5;

    private final KnowledgeService knowledgeService;

    public KnowledgeSearchToolDefinition(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String code() {
        return "knowledge_search";
    }

    @Override
    public String name() {
        return "知识库检索";
    }

    @Override
    public String description() {
        return "在指定知识库内做语义检索，返回与查询最相关的 top-k 文档切片（含相似度得分）。用于让流程/助手基于知识库内容作答。";
    }

    @Override
    public String category() {
        return "search";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> kbCode = new LinkedHashMap<>();
        kbCode.put("type", "string");
        kbCode.put("description", "知识库编码");

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "查询文本");

        Map<String, Object> topK = new LinkedHashMap<>();
        topK.put("type", "integer");
        topK.put("description", "返回条数，默认 5");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("kbCode", kbCode);
        properties.put("query", query);
        properties.put("topK", topK);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("kbCode", "query"));
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        try {
            String kbCode = str(params.get("kbCode"));
            String query = str(params.get("query"));
            int topK = intOf(params.get("topK"), DEFAULT_TOP_K);
            if (kbCode == null || kbCode.isBlank() || query == null || query.isBlank()) {
                return error("kbCode 与 query 必填");
            }
            List<VectorMatch> matches = knowledgeService.search(kbCode, query, topK, 0);
            List<Map<String, Object>> hits = new ArrayList<>(matches.size());
            for (VectorMatch m : matches) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("content", m.content());
                hit.put("score", m.score());
                hit.put("metadata", m.metadata());
                hits.add(hit);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("hits", hits);
            return result;
        } catch (Exception e) {
            log.warn("knowledge_search 执行失败（降级返回 error map）：{}", e.getMessage());
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
