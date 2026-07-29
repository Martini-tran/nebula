package com.nebula.common.ai.rag.memory;

import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.api.VectorReindexMarker;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.VectorQuery;
import com.nebula.common.ai.rag.VectorRecord;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.milvus.MilvusCollections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VectorLongTermMemory} 测试：双写、降级铁律（向量故障不影响 DB）、语义检索回填、text 空回退、
 * delete/clear 清向量、filterExpr 转义与 partition 分区。
 *
 * @author nebula
 */
class VectorLongTermMemoryTest {

    /**
     * 可控 DB 版记忆：记录调用、按 id 存取，search 返回预置结果。
     */
    private static class StubDb implements LongTermMemory {
        private final Map<String, MemoryRecord> store = new HashMap<>();
        private long seq = 0;
        int saveCalls = 0;
        int searchCalls = 0;
        int deleteCalls = 0;
        int clearCalls = 0;

        @Override
        public String save(MemoryRecord record) {
            saveCalls++;
            String id = String.valueOf(++seq);
            record.setId(id);
            store.put(id, record);
            return id;
        }

        @Override
        public List<String> saveAll(List<MemoryRecord> records) {
            List<String> ids = new ArrayList<>();
            for (MemoryRecord r : records) {
                ids.add(save(r));
            }
            return ids;
        }

        @Override
        public List<MemoryRecord> search(MemoryQuery query) {
            searchCalls++;
            return new ArrayList<>(store.values());
        }

        @Override
        public MemoryRecord get(String agentCode, String userId, String id) {
            return store.get(id);
        }

        @Override
        public void delete(String agentCode, String userId, String id) {
            deleteCalls++;
            store.remove(id);
        }

        @Override
        public void clear(String agentCode, String userId) {
            clearCalls++;
            store.clear();
        }
    }

    /**
     * 可控向量库：记录 upsert/search/delete 的实参，按需抛异常。
     */
    private static class StubVectorStore implements VectorStore {
        String lastUpsertCollection;
        List<VectorRecord> lastUpsertRecords;
        VectorQuery lastQuery;
        String lastDeleteCollection;
        String lastDeleteFilter;
        RuntimeException upsertThrow;
        RuntimeException searchThrow;
        RuntimeException deleteThrow;
        List<VectorMatch> searchResult = List.of();

        @Override
        public void upsert(List<com.nebula.common.ai.rag.VectorChunk> chunks, List<float[]> embeddings) {
        }

        @Override
        public List<com.nebula.common.ai.rag.VectorChunk> search(String kbCode, float[] queryVector, int topK) {
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
            if (deleteThrow != null) {
                throw deleteThrow;
            }
            this.lastDeleteCollection = collection;
            this.lastDeleteFilter = filterExpr;
        }
    }

    /**
     * 固定维度 embedding：返回单元素向量，记录被 embed 的文本。
     */
    private static class StubEmbedding implements EmbeddingProvider {
        String lastText;
        List<float[]> result = List.of(new float[]{0.1f, 0.2f, 0.3f});

        @Override
        public String code() {
            return "stub";
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            this.lastText = texts.isEmpty() ? null : texts.get(0);
            return result;
        }

        @Override
        public int dimension() {
            return 3;
        }
    }

    /**
     * 可控重索引标记器：记录置位/清位调用的 id。
     */
    private static class StubReindexMarker implements VectorReindexMarker {
        final List<String> needReindexIds = new ArrayList<>();
        final List<String> reindexedIds = new ArrayList<>();

        @Override
        public void markNeedReindex(String id) {
            needReindexIds.add(id);
        }

        @Override
        public void markReindexed(String id) {
            reindexedIds.add(id);
        }
    }

    /**
     * 极简 ObjectProvider：仅按测试需要支持 getIfAvailable（可注入 null 模拟标记器缺省）。
     */
    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public T getObject(Object... args) {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }
        };
    }

    private AiProperties.Rag.Memory config() {
        AiProperties.Rag.Memory c = new AiProperties.Rag.Memory();
        c.setMode("vector");
        c.setTopK(5);
        c.setMinScore(0.6);
        return c;
    }

    /**
     * 构造装饰器（默认带一个可用的标记器）。
     */
    private VectorLongTermMemory memory(LongTermMemory db, VectorStore vs, EmbeddingProvider emb,
                                        VectorReindexMarker marker) {
        return new VectorLongTermMemory(db, vs, emb, config(), providerOf(marker));
    }

    private MemoryRecord record(String content) {
        return new MemoryRecord()
                .setAgentCode("blog-agent")
                .setUserId("u1")
                .setConversationId("c1")
                .setType(MemoryType.SEMANTIC)
                .setContent(content);
    }

    @Test
    void save双写DB与向量并对齐scalars() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        StubEmbedding emb = new StubEmbedding();
        VectorLongTermMemory memory = memory(db, vs, emb, new StubReindexMarker());

        String id = memory.save(record("用户偏好深色主题"));

        assertEquals("1", id);
        assertEquals(1, db.saveCalls);
        assertEquals(MilvusCollections.MEMORY, vs.lastUpsertCollection);
        assertEquals(1, vs.lastUpsertRecords.size());
        VectorRecord vr = vs.lastUpsertRecords.get(0);
        assertEquals("1", vr.pk());
        assertEquals("用户偏好深色主题", vr.content());
        // scalars 对齐 nebula_memory 四个标量字段
        assertEquals("blog-agent", vr.scalars().get("agent_code"));
        assertEquals("u1", vr.scalars().get("user_id"));
        assertEquals("c1", vr.scalars().get("conversation_id"));
        assertEquals("SEMANTIC", vr.scalars().get("mem_type"));
    }

    @Test
    void 向量写失败降级不影响DB落库并置need_reindex() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        vs.upsertThrow = new IllegalStateException("Milvus 宕机");
        StubReindexMarker marker = new StubReindexMarker();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), marker);

        String id = memory.save(record("重要事实"));

        // DB 已落库拿到 id，向量写异常被吞
        assertEquals("1", id);
        assertEquals(1, db.saveCalls);
        assertNull(vs.lastUpsertRecords);
        // 向量写失败 → 置 need_reindex 交对账补偿
        assertEquals(List.of("1"), marker.needReindexIds);
    }

    @Test
    void 向量写失败时标记器缺省仅降级不外抛() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        vs.upsertThrow = new IllegalStateException("Milvus 宕机");
        // 标记器不可用（底层实现未实现 VectorReindexMarker）：ObjectProvider 返回 null
        VectorLongTermMemory memory = new VectorLongTermMemory(
                db, vs, new StubEmbedding(), config(), providerOf(null));

        // 不外抛即通过（退回纯 log.warn，对现有部署零破坏）
        String id = memory.save(record("重要事实"));
        assertEquals("1", id);
    }

    @Test
    void search走向量语义召回并回查DB() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        StubEmbedding emb = new StubEmbedding();
        VectorLongTermMemory memory = memory(db, vs, emb, new StubReindexMarker());

        // 先落两条，再让向量检索命中其一
        memory.save(record("深色主题偏好"));
        memory.save(record("英语沟通偏好"));
        vs.searchResult = List.of(new VectorMatch("2", "英语沟通偏好", 0.88, Map.of(), null));

        MemoryQuery q = new MemoryQuery().setAgentCode("blog-agent").setUserId("u1").setText("语言偏好");
        List<MemoryRecord> hits = memory.search(q);

        // embed 了 query 文本，走了向量检索，命中 pk 回查 DB
        assertEquals("语言偏好", emb.lastText);
        assertEquals(1, hits.size());
        assertEquals("2", hits.get(0).getId());
        assertEquals("英语沟通偏好", hits.get(0).getContent());
        // 未走 DB 语义回退
        assertEquals(0, db.searchCalls);
        // VectorQuery 参数：collection/partition/topK/minScore
        assertEquals(MilvusCollections.MEMORY, vs.lastQuery.collection());
        assertEquals("blog-agent", vs.lastQuery.partition());
        assertEquals(5, vs.lastQuery.topK());
        assertEquals(0.6, vs.lastQuery.minScore());
    }

    @Test
    void search文本为空回退DB检索() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());
        memory.save(record("任意记忆"));

        MemoryQuery q = new MemoryQuery().setAgentCode("blog-agent").setUserId("u1");
        memory.search(q);

        // 无 text 无法语义召回，回退 DB，不触发向量检索
        assertEquals(1, db.searchCalls);
        assertNull(vs.lastQuery);
    }

    @Test
    void search向量故障降级DB检索() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        vs.searchThrow = new IllegalStateException("Milvus 检索超时");
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());
        memory.save(record("任意记忆"));

        MemoryQuery q = new MemoryQuery().setAgentCode("blog-agent").setUserId("u1").setText("查询");
        memory.search(q);

        // 向量检索抛异常 → 回退 DB
        assertEquals(1, db.searchCalls);
    }

    @Test
    void search的filterExpr含user与type不含agent() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());

        MemoryQuery q = new MemoryQuery()
                .setAgentCode("blog-agent")
                .setUserId("u1")
                .setType(MemoryType.ENTITY)
                .setText("查询");
        memory.search(q);

        String filter = vs.lastQuery.filterExpr();
        assertTrue(filter.contains("user_id == \"u1\""), filter);
        assertTrue(filter.contains("mem_type == \"ENTITY\""), filter);
        // agent_code 走 partition，不在 filterExpr 里
        assertTrue(!filter.contains("agent_code"), filter);
    }

    @Test
    void filterExpr对双引号转义防注入() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());

        MemoryQuery q = new MemoryQuery()
                .setAgentCode("a")
                .setUserId("u\" or \"1\"==\"1")
                .setText("查询");
        memory.search(q);

        String filter = vs.lastQuery.filterExpr();
        // 注入的双引号被转义为 \"，不构成表达式截断
        assertTrue(filter.contains("\\\""), filter);
    }

    @Test
    void delete清DB与向量() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());

        memory.delete("blog-agent", "u1", "42");

        assertEquals(1, db.deleteCalls);
        assertEquals(MilvusCollections.MEMORY, vs.lastDeleteCollection);
        assertEquals("pk == \"42\"", vs.lastDeleteFilter);
    }

    @Test
    void clear按agent与user清向量() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());

        memory.clear("blog-agent", "u1");

        assertEquals(1, db.clearCalls);
        assertEquals("agent_code == \"blog-agent\" && user_id == \"u1\"", vs.lastDeleteFilter);
    }

    @Test
    void 向量删除失败不外抛并置need_reindex() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        vs.deleteThrow = new IllegalStateException("Milvus 宕机");
        StubReindexMarker marker = new StubReindexMarker();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), marker);

        // DB 删除照常，向量删除异常被吞，置 need_reindex 交对账清残留
        memory.delete("blog-agent", "u1", "42");
        assertEquals(1, db.deleteCalls);
        assertEquals(List.of("42"), marker.needReindexIds);
    }

    @Test
    void reindex只重写向量不碰DB成功返回true() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());

        MemoryRecord r = record("待补偿记忆").setId("7");
        boolean ok = memory.reindex(r);

        assertTrue(ok);
        // 只 upsert 向量，不走 delegate.save
        assertEquals(0, db.saveCalls);
        assertEquals(MilvusCollections.MEMORY, vs.lastUpsertCollection);
        assertEquals("7", vs.lastUpsertRecords.get(0).pk());
    }

    @Test
    void reindex向量写失败返回false不外抛() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        vs.upsertThrow = new IllegalStateException("Milvus 宕机");
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());

        // 失败返回 false（对账保留 need_reindex 待下轮），不外抛
        assertTrue(!memory.reindex(record("x").setId("9")));
    }

    @Test
    void reindex缺id返回false() {
        StubDb db = new StubDb();
        StubVectorStore vs = new StubVectorStore();
        VectorLongTermMemory memory = memory(db, vs, new StubEmbedding(), new StubReindexMarker());

        assertTrue(!memory.reindex(record("无id")));
    }
}
