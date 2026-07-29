package com.nebula.common.ai.rag;

import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.api.VectorReindexMarker;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.flowexample.FlowExampleService;
import com.nebula.common.ai.rag.memory.VectorLongTermMemory;
import com.nebula.common.ai.rag.milvus.MilvusCollections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多租户隔离核验（批次4 C）。
 *
 * <p>把「向量检索按租户维度隔离」这条横切不变量集中断言为回归防线：四场景各有一个 partitionKey——
 * 记忆 {@code agent_code}、few-shot {@code owner}、知识库 {@code kb_code}、工具目录 {@code item_type}——
 * 检索请求必须把租户键放进 {@link VectorQuery#partition()}（Milvus 物理分区裁剪，硬隔离），敏感的次级维度
 * （如记忆的 {@code user_id}）放进 {@code filterExpr}。本测试证明：不同租户的检索落到不同分区、不会串味。
 *
 * <p>覆盖 memory 与 few-shot 两条检索路径（仅依赖 VectorStore+embedding，可独立核验）。知识库检索同构——
 * {@code KnowledgeService.search} 以 {@code new VectorQuery(KB, kbCode, ...)} 令 partition=kbCode，见其实现；
 * 因其检索还需库元数据 Mapper（维度校验），不在本纯 SDK 测试内重建依赖，隔离键结构与此处一致。
 *
 * <p>意义：将来若有人改动检索过滤逻辑、误删了 partition 上的租户键，本测试会失败——这正是多租户串味
 * （安全/隐私级事故）最需要的回归拦截。
 *
 * @author nebula
 */
class MultiTenantIsolationTest {

    /**
     * 捕获每次检索的 VectorQuery。
     */
    private static class CapturingVectorStore implements VectorStore {
        VectorQuery lastQuery;

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
            this.lastQuery = query;
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

    private VectorLongTermMemory memory(CapturingVectorStore vs) {
        AiProperties.Rag.Memory c = new AiProperties.Rag.Memory();
        c.setMode("vector");
        c.setTopK(5);
        c.setMinScore(0.6);
        return new VectorLongTermMemory(new NoopDb(), vs, new StubEmbedding(), c,
                providerOf((VectorReindexMarker) null));
    }

    @Test
    void 记忆检索按agent_code分区且user_id进filter() {
        CapturingVectorStore vs = new CapturingVectorStore();
        VectorLongTermMemory memory = memory(vs);

        memory.search(new MemoryQuery()
                .setAgentCode("agent-A").setUserId("user-1").setType(MemoryType.SEMANTIC).setText("查询"));

        assertEquals(MilvusCollections.MEMORY, vs.lastQuery.collection());
        // agent_code 走硬分区
        assertEquals("agent-A", vs.lastQuery.partition());
        // user_id 走 filterExpr（同 agent 下按用户再隔离）
        assertTrue(vs.lastQuery.filterExpr().contains("user_id == \"user-1\""), vs.lastQuery.filterExpr());
    }

    @Test
    void 不同agent的记忆检索落到不同分区不串味() {
        CapturingVectorStore vs = new CapturingVectorStore();
        VectorLongTermMemory memory = memory(vs);

        memory.search(new MemoryQuery().setAgentCode("agent-A").setUserId("u").setText("q"));
        String partitionA = vs.lastQuery.partition();
        memory.search(new MemoryQuery().setAgentCode("agent-B").setUserId("u").setText("q"));
        String partitionB = vs.lastQuery.partition();

        // 两个 agent 的检索分区不同——物理分区裁剪保证 A 检索不到 B 的记忆
        assertNotEquals(partitionA, partitionB);
        assertEquals("agent-A", partitionA);
        assertEquals("agent-B", partitionB);
    }

    @Test
    void few_shot检索按owner分区() {
        CapturingVectorStore vs = new CapturingVectorStore();
        FlowExampleService service = new FlowExampleService(vs, new StubEmbedding());

        service.recall("我想做一个自动写博客的流程", 3, 0.6);

        assertEquals(MilvusCollections.FLOW_EXAMPLE, vs.lastQuery.collection());
        // few-shot 全局共享，partition 固定为 OWNER_GLOBAL（隔离键存在且确定，非 null）
        assertEquals(FlowExampleService.OWNER_GLOBAL, vs.lastQuery.partition());
    }

    @Test
    void 四场景partitionKey互不相同保证跨场景不混() {
        // 四个 collection 逻辑名互异 + 各自 partitionKey 语义互异，跨场景天然不可能命中彼此
        List<String> collections = new ArrayList<>(List.of(
                MilvusCollections.KB,
                MilvusCollections.FLOW_EXAMPLE,
                MilvusCollections.MEMORY,
                MilvusCollections.TOOL_CATALOG));
        assertEquals(4, collections.stream().distinct().count(), "四场景 collection 逻辑名必须互异");
    }
}
