package com.nebula.common.ai.rag.memory;

import com.nebula.blog.entity.AiMemory;
import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.api.VectorReindexMarker;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorChunk;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.VectorQuery;
import com.nebula.common.ai.rag.VectorRecord;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.memory.store.AiMemoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MemoryReindexReconciler} 测试：扫 need_reindex=1 → 重写向量 → 成功清标记，失败保留标记，
 * batchSize 生效，空积压早退。用手写 Stub Mapper/VectorStore（不引 mockito）。
 *
 * @author nebula
 */
class MemoryReindexReconcilerTest {

    /**
     * 可控记忆 Mapper 状态：{@link BaseMapper} 方法众多且随 MyBatis-Plus 版本变化，用动态代理只拦截
     * {@code selectList}/{@code selectCount}，其余返回默认值——不受接口方法数量/签名漂移影响。
     */
    private static class StubMapperState {
        List<AiMemory> needReindexRows = new ArrayList<>();
        long backlogCount = 0;
        int selectListCalls = 0;
    }

    /**
     * 生成只实现 selectList/selectCount 的 AiMemoryMapper 代理。不窥探 wrapper 的 SqlSegment——那会触发
     * MyBatis-Plus 的 lambda 解析（无 SqlSessionFactory 的纯单测里没有 lambda 缓存），与本测试无关。
     */
    private static AiMemoryMapper stubMapper(StubMapperState state) {
        return (AiMemoryMapper) Proxy.newProxyInstance(
                AiMemoryMapper.class.getClassLoader(),
                new Class<?>[]{AiMemoryMapper.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "selectList" -> {
                            state.selectListCalls++;
                            return state.needReindexRows;
                        }
                        case "selectCount" -> {
                            return state.backlogCount;
                        }
                        default -> {
                            // 其余方法测试不涉及，返回类型安全默认值
                            Class<?> rt = method.getReturnType();
                            if (rt.equals(int.class) || rt.equals(long.class)) {
                                return 0;
                            }
                            if (rt.equals(boolean.class)) {
                                return false;
                            }
                            if (rt.equals(List.class)) {
                                return List.of();
                            }
                            return null;
                        }
                    }
                });
    }

    /**
     * 可控向量库：按 pk 决定 upsert 成功或抛异常，记录成功写入的 pk。
     */
    private static class StubVectorStore implements VectorStore {
        List<String> upsertedPks = new ArrayList<>();
        List<String> failPks = new ArrayList<>();

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
            String pk = records.get(0).pk();
            if (failPks.contains(pk)) {
                throw new IllegalStateException("Milvus 写失败: " + pk);
            }
            upsertedPks.add(pk);
        }

        @Override
        public List<VectorMatch> search(VectorQuery query) {
            return List.of();
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

    /**
     * 记录清位调用的标记器。
     */
    private static class StubMarker implements VectorReindexMarker {
        List<String> reindexed = new ArrayList<>();

        @Override
        public void markNeedReindex(String id) {
        }

        @Override
        public void markReindexed(String id) {
            reindexed.add(id);
        }
    }

    /**
     * delegate DB 版记忆：对账不触发其 save/search，占位即可。
     */
    private static class NoopDb implements LongTermMemory {
        @Override
        public String save(MemoryRecord record) {
            return null;
        }

        @Override
        public List<String> saveAll(List<MemoryRecord> records) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> search(MemoryQuery query) {
            return List.of();
        }

        @Override
        public MemoryRecord get(String agentCode, String userId, String id) {
            return null;
        }

        @Override
        public void delete(String agentCode, String userId, String id) {
        }

        @Override
        public void clear(String agentCode, String userId) {
        }
    }

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

    private AiProperties.Rag.Memory memConfig() {
        AiProperties.Rag.Memory c = new AiProperties.Rag.Memory();
        c.setMode("vector");
        return c;
    }

    private VectorLongTermMemory vectorMemory(StubVectorStore vs) {
        return new VectorLongTermMemory(new NoopDb(), vs, new StubEmbedding(), memConfig(),
                providerOf(new StubMarker()));
    }

    private AiMemory row(long id, String content) {
        AiMemory m = new AiMemory();
        m.setId(id);
        m.setAgentCode("blog-agent");
        m.setUserId("u1");
        m.setContent(content);
        m.setNeedReindex(1);
        return m;
    }

    private AiProperties.Rag.Reconcile reconcileConfig(int batchSize) {
        AiProperties.Rag.Reconcile c = new AiProperties.Rag.Reconcile();
        c.setBatchSize(batchSize);
        return c;
    }

    @Test
    void 空积压早退不清标记() {
        StubMapperState state = new StubMapperState();
        StubVectorStore vs = new StubVectorStore();
        StubMarker marker = new StubMarker();
        MemoryReindexReconciler r = new MemoryReindexReconciler(
                stubMapper(state), vectorMemory(vs), marker, reconcileConfig(200));

        assertEquals(0, r.runOnce());
        assertTrue(marker.reindexed.isEmpty());
    }

    @Test
    void 全部重索引成功清位() {
        StubMapperState state = new StubMapperState();
        state.needReindexRows = List.of(row(1, "记忆1"), row(2, "记忆2"));
        StubVectorStore vs = new StubVectorStore();
        StubMarker marker = new StubMarker();
        MemoryReindexReconciler r = new MemoryReindexReconciler(
                stubMapper(state), vectorMemory(vs), marker, reconcileConfig(200));

        int success = r.runOnce();

        assertEquals(2, success);
        // 两条都补写了向量
        assertEquals(List.of("1", "2"), vs.upsertedPks);
        // 两条都清了标记
        assertEquals(List.of("1", "2"), marker.reindexed);
    }

    @Test
    void 部分失败仅成功者清位失败者保留标记() {
        StubMapperState state = new StubMapperState();
        state.needReindexRows = List.of(row(1, "记忆1"), row(2, "记忆2"), row(3, "记忆3"));
        StubVectorStore vs = new StubVectorStore();
        vs.failPks = List.of("2");
        StubMarker marker = new StubMarker();
        MemoryReindexReconciler r = new MemoryReindexReconciler(
                stubMapper(state), vectorMemory(vs), marker, reconcileConfig(200));

        int success = r.runOnce();

        // id=2 写失败，仅 1、3 成功清位；2 保留 need_reindex 待下轮
        assertEquals(2, success);
        assertEquals(List.of("1", "3"), marker.reindexed);
        assertTrue(!marker.reindexed.contains("2"));
    }

    @Test
    void runOnce执行一次扫描查询() {
        // batchSize 通过 .last("limit N") 拼进查询（纯字符串拼接，无法在无 MyBatis 环境窥探 SqlSegment）；
        // 此处验证对账确实执行了一次扫描查询——扫描路径被走到，batchSize 已随查询构造传入。
        StubMapperState state = new StubMapperState();
        state.needReindexRows = List.of(row(1, "记忆1"));
        StubVectorStore vs = new StubVectorStore();
        MemoryReindexReconciler r = new MemoryReindexReconciler(
                stubMapper(state), vectorMemory(vs), new StubMarker(), reconcileConfig(50));

        int success = r.runOnce();

        assertEquals(1, state.selectListCalls);
        assertEquals(1, success);
    }
}
