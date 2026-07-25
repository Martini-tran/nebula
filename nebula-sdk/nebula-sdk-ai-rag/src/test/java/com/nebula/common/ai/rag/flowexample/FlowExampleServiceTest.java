package com.nebula.common.ai.rag.flowexample;

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
 * {@link FlowExampleService} 测试：upsert 的 pk/scalars/owner 对齐、recall 的 VectorQuery 参数
 * （collection/partition=owner/topK/minScore）、命中映射为 FlowExample，以及向量故障降级铁律（写/召回失败不外抛）。
 *
 * @author nebula
 */
class FlowExampleServiceTest {

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

    @Test
    void upsert写入FLOW_EXAMPLE并对齐pk与scalars() {
        StubVectorStore vs = new StubVectorStore();
        StubEmbedding emb = new StubEmbedding();
        FlowExampleService service = new FlowExampleService(vs, emb);

        service.upsert("daily-blog", "每日博客生成", "按目录逐篇生成博客并落库", "START,LOOP,LLM,END");

        assertEquals(MilvusCollections.FLOW_EXAMPLE, vs.lastUpsertCollection);
        assertEquals(1, vs.lastUpsertRecords.size());
        VectorRecord vr = vs.lastUpsertRecords.get(0);
        // pk = flowCode
        assertEquals("daily-blog", vr.pk());
        // content = 描述副本
        assertEquals("按目录逐篇生成博客并落库", vr.content());
        // scalars 对齐 nebula_flow_example：owner=global + flow_code + node_types
        assertEquals(FlowExampleService.OWNER_GLOBAL, vr.scalars().get("owner"));
        assertEquals("daily-blog", vr.scalars().get("flow_code"));
        assertEquals("START,LOOP,LLM,END", vr.scalars().get("node_types"));
        // name 存 metadata 供召回回显
        assertEquals("每日博客生成", vr.metadata().get("name"));
        // embed 文本为「名称：描述」
        assertTrue(emb.lastTexts.get(0).contains("每日博客生成"), emb.lastTexts.get(0));
    }

    @Test
    void upsert的flowCode为空跳过() {
        StubVectorStore vs = new StubVectorStore();
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        service.upsert("  ", "名称", "描述", "START");

        assertNull(vs.lastUpsertRecords);
    }

    @Test
    void indexAll批量索引() {
        StubVectorStore vs = new StubVectorStore();
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        int n = service.indexAll(List.of(
                new FlowExampleService.FlowExampleSource("f1", "流程1", "描述1", "START,END"),
                new FlowExampleService.FlowExampleSource("f2", "流程2", "描述2", "START,LLM,END")));

        assertEquals(2, n);
        // 批量：一次 embed + 一次 upsert 写入全部 records（非逐条），保序
        assertEquals(2, vs.lastUpsertRecords.size());
        assertEquals("f1", vs.lastUpsertRecords.get(0).pk());
        assertEquals("f2", vs.lastUpsertRecords.get(1).pk());
    }

    @Test
    void indexAll跳过null与flowCode为空的条目() {
        StubVectorStore vs = new StubVectorStore();
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        int n = service.indexAll(java.util.Arrays.asList(
                new FlowExampleService.FlowExampleSource("f1", "流程1", "描述1", "START,END"),
                null,
                new FlowExampleService.FlowExampleSource("  ", "空码", "应跳过", "START")));

        // 返回实际写入数（非尝试数），仅 f1 有效
        assertEquals(1, n);
        assertEquals(1, vs.lastUpsertRecords.size());
        assertEquals("f1", vs.lastUpsertRecords.get(0).pk());
    }

    @Test
    void recall走向量召回并映射为FlowExample() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchResult = List.of(
                new VectorMatch("daily-blog", "按目录逐篇生成博客", 0.88,
                        Map.of("flow_code", "daily-blog"), Map.of("name", "每日博客生成")));
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        List<FlowExample> examples = service.recall("我想做一个自动写博客的流程", 3, 0.6);

        assertEquals(1, examples.size());
        FlowExample e = examples.get(0);
        assertEquals("daily-blog", e.flowCode());
        assertEquals("每日博客生成", e.name());
        assertEquals("按目录逐篇生成博客", e.description());
        assertEquals(0.88, e.score());
        // VectorQuery 参数：collection=FLOW_EXAMPLE，partition=global，topK=3，minScore=0.6
        assertEquals(MilvusCollections.FLOW_EXAMPLE, vs.lastQuery.collection());
        assertEquals(FlowExampleService.OWNER_GLOBAL, vs.lastQuery.partition());
        assertEquals(3, vs.lastQuery.topK());
        assertEquals(0.6, vs.lastQuery.minScore());
    }

    @Test
    void recall空查询返回空不触发检索() {
        StubVectorStore vs = new StubVectorStore();
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        assertTrue(service.recall("  ", 3, 0.6).isEmpty());
        assertNull(vs.lastQuery);
    }

    @Test
    void recall命中缺flow_code标量时回退pk() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchResult = List.of(new VectorMatch("f9", "描述", 0.7, Map.of(), null));
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        List<FlowExample> examples = service.recall("查询", 3, 0);

        assertEquals("f9", examples.get(0).flowCode());
        assertNull(examples.get(0).name());
    }

    @Test
    void upsert向量写失败降级不外抛() {
        StubVectorStore vs = new StubVectorStore();
        vs.upsertThrow = new IllegalStateException("Milvus 宕机");
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        // 不抛异常即通过
        service.upsert("f1", "名称", "描述", "START");
    }

    @Test
    void recall向量召回失败降级返回空不外抛() {
        StubVectorStore vs = new StubVectorStore();
        vs.searchThrow = new IllegalStateException("Milvus 检索超时");
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        assertTrue(service.recall("查询", 3, 0.6).isEmpty());
    }
}
