package com.nebula.common.ai.rag.toolcatalog;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorChunk;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.VectorQuery;
import com.nebula.common.ai.rag.VectorRecord;
import com.nebula.common.ai.rag.VectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SearchToolsToolDefinition} 测试：命中回填 name/description/paramsSchema、自指过滤（search_tools 不回自身）、
 * 缺参 error、检索异常降级、注册表不可用时回退仅 code、元数据契约。
 *
 * @author nebula
 */
class SearchToolsToolDefinitionTest {

    /**
     * 可控向量库：search 返回预置命中，或按需抛异常。
     */
    private static class StubVectorStore implements VectorStore {
        List<VectorMatch> searchResult = List.of();
        RuntimeException searchThrow;

        @Override
        public void upsert(List<VectorChunk> chunks, List<float[]> embeddings) {
        }

        @Override
        public List<VectorChunk> search(String kbCode, float[] queryVector, int topK) {
            return List.of();
        }

        @Override
        public void deleteByDoc(String kbCode, String docId) {
        }

        @Override
        public void upsert(String collection, List<VectorRecord> records) {
        }

        @Override
        public List<VectorMatch> search(VectorQuery query) {
            if (searchThrow != null) {
                throw searchThrow;
            }
            return searchResult;
        }

        @Override
        public void delete(String collection, String filterExpr) {
        }
    }

    private static class StubEmbedding implements EmbeddingProvider {
        @Override
        public String code() {
            return "stub";
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(t -> new float[]{0.1f, 0.2f, 0.3f}).toList();
        }

        @Override
        public int dimension() {
            return 3;
        }
    }

    private static ToolDefinition tool(String code, String name, String description) {
        return new ToolDefinition() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Map<String, Object> paramsSchema() {
                return Map.of("type", "object");
            }

            @Override
            public Object invoke(Map<String, Object> params, ToolContext ctx) {
                return null;
            }
        };
    }

    /**
     * 只实现 getIfAvailable 的 ObjectProvider（其余方法测试不触及）。
     */
    private static ObjectProvider<ToolRegistry> provider(ToolRegistry registry) {
        return new ObjectProvider<>() {
            @Override
            public ToolRegistry getObject(Object... args) {
                return registry;
            }

            @Override
            public ToolRegistry getIfAvailable() {
                return registry;
            }

            @Override
            public ToolRegistry getIfUnique() {
                return registry;
            }

            @Override
            public ToolRegistry getObject() {
                return registry;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tools(Object result) {
        return (List<Map<String, Object>>) ((Map<String, Object>) result).get("tools");
    }

    private ToolCatalogService catalog(StubVectorStore vs) {
        return new ToolCatalogService(vs, new StubEmbedding());
    }

    @Test
    void 命中回填注册表元数据() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchResult = List.of(
                new VectorMatch("send_email", "发送邮件", 0.9, Map.of("code", "send_email"), null));
        ToolRegistry registry = new ToolRegistry(List.of(tool("send_email", "发送邮件", "向指定地址发送邮件")));
        SearchToolsToolDefinition def = new SearchToolsToolDefinition(catalog(vs), provider(registry));

        Object result = def.invoke(Map.of("query", "给客户发通知"), null);

        assertTrue((Boolean) ((Map<String, Object>) result).get("ok"));
        List<Map<String, Object>> hits = tools(result);
        assertEquals(1, hits.size());
        assertEquals("send_email", hits.get(0).get("code"));
        assertEquals("发送邮件", hits.get(0).get("name"));
        assertEquals("向指定地址发送邮件", hits.get(0).get("description"));
        assertEquals(Map.of("type", "object"), hits.get(0).get("paramsSchema"));
    }

    @Test
    void 过滤掉命中的search_tools自身() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchResult = List.of(
                new VectorMatch("search_tools", "工具语义检索", 0.95, Map.of("code", "search_tools"), null),
                new VectorMatch("send_email", "发送邮件", 0.9, Map.of("code", "send_email"), null));
        ToolRegistry registry = new ToolRegistry(List.of(tool("send_email", "发送邮件", "描述")));
        SearchToolsToolDefinition def = new SearchToolsToolDefinition(catalog(vs), provider(registry));

        List<Map<String, Object>> hits = tools(def.invoke(Map.of("query", "发通知"), null));

        // search_tools 自身被过滤，只剩 send_email
        assertEquals(1, hits.size());
        assertEquals("send_email", hits.get(0).get("code"));
    }

    @Test
    void 缺query返回error() {
        StubVectorStore vs = new StubVectorStore();
        SearchToolsToolDefinition def = new SearchToolsToolDefinition(catalog(vs), provider(new ToolRegistry(List.of())));

        Map<String, Object> result = (Map<String, Object>) def.invoke(Map.of(), null);

        assertFalse((Boolean) result.get("ok"));
        assertTrue(String.valueOf(result.get("error")).contains("query"));
    }

    @Test
    void 检索异常降级返回errorMap() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchThrow = new IllegalStateException("Milvus 宕机");
        // ToolCatalogService.search 内部已吞异常返回空，故这里命中为空、ok=true、tools 为空——验证不外抛
        SearchToolsToolDefinition def = new SearchToolsToolDefinition(catalog(vs), provider(new ToolRegistry(List.of())));

        Map<String, Object> result = (Map<String, Object>) def.invoke(Map.of("query", "查询"), null);

        assertTrue((Boolean) result.get("ok"));
        assertTrue(tools(result).isEmpty());
    }

    @Test
    void 注册表不可用时回退仅返回code() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchResult = List.of(
                new VectorMatch("send_email", "发送邮件", 0.9, Map.of("code", "send_email"), null));
        // provider 返回 null 模拟注册表未就绪
        SearchToolsToolDefinition def = new SearchToolsToolDefinition(catalog(vs), provider(null));

        List<Map<String, Object>> hits = tools(def.invoke(Map.of("query", "发通知"), null));

        assertEquals(1, hits.size());
        assertEquals("send_email", hits.get(0).get("code"));
        // 注册表不可用 → 无 name 元数据
        assertFalse(hits.get(0).containsKey("name"));
    }

    @Test
    void 元数据契约() {
        SearchToolsToolDefinition def = new SearchToolsToolDefinition(
                catalog(new StubVectorStore()), provider(new ToolRegistry(List.of())));

        assertEquals("search_tools", def.code());
        assertEquals("search", def.category());
        Map<String, Object> schema = def.paramsSchema();
        assertEquals(List.of("query"), schema.get("required"));
        assertTrue(((Map<String, Object>) schema.get("properties")).containsKey("query"));
    }
}
