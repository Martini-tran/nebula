package com.nebula.common.ai.rag.knowledge;

import com.nebula.common.ai.rag.VectorMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KnowledgeSearchToolDefinition} 测试：正常命中封装与降级铁律（异常不外抛，返回 error map）。
 *
 * @author nebula
 */
class KnowledgeSearchToolDefinitionTest {

    /**
     * 可控 KnowledgeService：按需返回命中或抛异常，构造参数全传 null（覆盖了 search 方法，父类字段不参与）。
     */
    private static class StubKnowledgeService extends KnowledgeService {
        private final List<VectorMatch> matches;
        private final RuntimeException toThrow;

        StubKnowledgeService(List<VectorMatch> matches, RuntimeException toThrow) {
            super(null, null, null, null, null, null, 0, 0);
            this.matches = matches;
            this.toThrow = toThrow;
        }

        @Override
        public List<VectorMatch> search(String kbCode, String query, int topK, double minScore) {
            if (toThrow != null) {
                throw toThrow;
            }
            return matches;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void 命中封装为hits列表() {
        List<VectorMatch> matches = List.of(
                new VectorMatch("pk1", "内容A", 0.9, Map.of(), Map.of("title", "T")),
                new VectorMatch("pk2", "内容B", 0.7, Map.of(), Map.of()));
        KnowledgeSearchToolDefinition tool = new KnowledgeSearchToolDefinition(
                new StubKnowledgeService(matches, null));

        Object result = tool.invoke(Map.of("kbCode", "kb1", "query", "问题", "topK", 2), null);
        assertNotNull(result);
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(Boolean.TRUE, map.get("ok"));
        List<Map<String, Object>> hits = (List<Map<String, Object>>) map.get("hits");
        assertEquals(2, hits.size());
        assertEquals("内容A", hits.get(0).get("content"));
        assertEquals(0.9, hits.get(0).get("score"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 缺参返回error而非命中() {
        KnowledgeSearchToolDefinition tool = new KnowledgeSearchToolDefinition(
                new StubKnowledgeService(List.of(), null));
        Object result = tool.invoke(Map.of("query", "问题"), null);
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(Boolean.FALSE, map.get("ok"));
        assertNotNull(map.get("error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 检索异常降级为error不外抛() {
        KnowledgeSearchToolDefinition tool = new KnowledgeSearchToolDefinition(
                new StubKnowledgeService(null, new IllegalStateException("Milvus 宕机")));
        Object result = tool.invoke(Map.of("kbCode", "kb1", "query", "问题"), null);
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(Boolean.FALSE, map.get("ok"));
        assertTrue(String.valueOf(map.get("error")).contains("Milvus"));
    }

    @Test
    void 工具元数据契约稳定() {
        KnowledgeSearchToolDefinition tool = new KnowledgeSearchToolDefinition(
                new StubKnowledgeService(List.of(), null));
        assertEquals("knowledge_search", tool.code());
        assertEquals("search", tool.category());
        assertFalse(tool.paramsSchema().isEmpty());
    }
}
