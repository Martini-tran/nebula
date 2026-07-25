package com.nebula.common.ai.rag.toolcatalog;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorChunk;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.VectorQuery;
import com.nebula.common.ai.rag.VectorRecord;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.milvus.MilvusCollections;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolCatalogService} 测试：index 的 pk/scalars 对齐、search 的 VectorQuery 参数（collection/partition/topK）、
 * 命中 code 回收，以及向量故障降级铁律（写失败返回 0、检索失败返回空，均不外抛）。
 *
 * @author nebula
 */
class ToolCatalogServiceTest {

    /**
     * 可控向量库：记录 upsert/search 实参，按需抛异常。
     */
    private static class StubVectorStore implements VectorStore {
        String lastUpsertCollection;
        List<VectorRecord> lastUpsertRecords;
        VectorQuery lastQuery;
        RuntimeException upsertThrow;
        RuntimeException searchThrow;
        List<VectorMatch> searchResult = List.of();

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
            if (upsertThrow != null) {
                throw upsertThrow;
            }
            this.lastUpsertCollection = collection;
            this.lastUpsertRecords = records;
        }

        @Override
        public List<VectorMatch> search(VectorQuery query) {
            this.lastQuery = query;
            if (searchThrow != null) {
                throw searchThrow;
            }
            return searchResult;
        }

        @Override
        public void delete(String collection, String filterExpr) {
        }
    }

    /**
     * 固定维度 embedding：返回与入参等长的向量列表，记录被 embed 的文本。
     */
    private static class StubEmbedding implements EmbeddingProvider {
        List<String> lastTexts;

        @Override
        public String code() {
            return "stub";
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            this.lastTexts = texts;
            return texts.stream().map(t -> new float[]{0.1f, 0.2f, 0.3f}).toList();
        }

        @Override
        public int dimension() {
            return 3;
        }
    }

    /**
     * 极简工具定义（仅测试用）。
     */
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
            public Object invoke(Map<String, Object> params, ToolContext ctx) {
                return null;
            }
        };
    }

    @Test
    void index写入TOOL_CATALOG并对齐pk与scalars() {
        StubVectorStore vs = new StubVectorStore();
        StubEmbedding emb = new StubEmbedding();
        ToolCatalogService service = new ToolCatalogService(vs, emb);

        int n = service.index(List.of(
                tool("send_email", "发送邮件", "向指定地址发送邮件"),
                tool("http_get", "HTTP GET", "发起 GET 请求")));

        assertEquals(2, n);
        assertEquals(MilvusCollections.TOOL_CATALOG, vs.lastUpsertCollection);
        assertEquals(2, vs.lastUpsertRecords.size());
        VectorRecord first = vs.lastUpsertRecords.get(0);
        // pk = 工具 code
        assertEquals("send_email", first.pk());
        // scalars 对齐 nebula_tool_catalog：item_type=tool + code
        assertEquals(ToolCatalogService.ITEM_TYPE_TOOL, first.scalars().get("item_type"));
        assertEquals("send_email", first.scalars().get("code"));
        // embed 文本为「名称：描述」
        assertTrue(emb.lastTexts.get(0).contains("发送邮件"), emb.lastTexts.get(0));
        assertTrue(emb.lastTexts.get(0).contains("向指定地址发送邮件"), emb.lastTexts.get(0));
    }

    @Test
    void index跳过code为空的工具() {
        StubVectorStore vs = new StubVectorStore();
        ToolCatalogService service = new ToolCatalogService(vs, new StubEmbedding());

        int n = service.index(List.of(
                tool("valid", "有效", "有效工具"),
                tool("  ", "空码", "code 空白应跳过")));

        assertEquals(1, n);
        assertEquals(1, vs.lastUpsertRecords.size());
        assertEquals("valid", vs.lastUpsertRecords.get(0).pk());
    }

    @Test
    void search走向量检索并回收命中code() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchResult = List.of(
                new VectorMatch("send_email", "发送邮件", 0.91, Map.of("code", "send_email"), null),
                new VectorMatch("http_get", "HTTP GET", 0.77, Map.of("code", "http_get"), null));
        ToolCatalogService service = new ToolCatalogService(vs, new StubEmbedding());

        List<String> codes = service.search("我要给客户发通知", 5);

        assertEquals(List.of("send_email", "http_get"), codes);
        // VectorQuery 参数：collection=TOOL_CATALOG，partition=tool，topK=5
        assertEquals(MilvusCollections.TOOL_CATALOG, vs.lastQuery.collection());
        assertEquals(ToolCatalogService.ITEM_TYPE_TOOL, vs.lastQuery.partition());
        assertEquals(5, vs.lastQuery.topK());
    }

    @Test
    void search命中缺code标量时回退pk() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchResult = List.of(new VectorMatch("send_email", "发送邮件", 0.91, Map.of(), null));
        ToolCatalogService service = new ToolCatalogService(vs, new StubEmbedding());

        List<String> codes = service.search("发通知", 5);

        assertEquals(List.of("send_email"), codes);
    }

    @Test
    void search空查询直接返回空不触发检索() {
        StubVectorStore vs = new StubVectorStore();
        ToolCatalogService service = new ToolCatalogService(vs, new StubEmbedding());

        assertTrue(service.search("  ", 5).isEmpty());
        assertNull(vs.lastQuery);
    }

    @Test
    void index向量写失败降级返回0不外抛() {
        StubVectorStore vs = new StubVectorStore();
        vs.upsertThrow = new IllegalStateException("Milvus 宕机");
        ToolCatalogService service = new ToolCatalogService(vs, new StubEmbedding());

        int n = service.index(List.of(tool("send_email", "发送邮件", "描述")));

        assertEquals(0, n);
    }

    @Test
    void search向量故障降级返回空不外抛() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchThrow = new IllegalStateException("Milvus 检索超时");
        ToolCatalogService service = new ToolCatalogService(vs, new StubEmbedding());

        assertTrue(service.search("查询", 5).isEmpty());
    }
}
