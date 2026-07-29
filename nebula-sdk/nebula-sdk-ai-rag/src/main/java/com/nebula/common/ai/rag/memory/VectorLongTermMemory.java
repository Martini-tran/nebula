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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量语义召回版长期记忆（场景③）。
 *
 * <p>装饰器：包住底层数据库版 {@link LongTermMemory}（真相源，落 {@code ai_memory}），在其上叠加向量能力——
 * 写入时双写（DB + Milvus {@code nebula_memory}），检索时走向量语义召回替代 LIKE 关键词。仅当
 * {@code nebula.ai.rag.memory.mode=vector} 且 embedding + 向量库就绪时装配（见 {@code MemoryVectorAutoConfiguration}）。
 *
 * <p><b>为何用装饰器</b>：DB 是记忆的真相源，向量库是召回加速层。这样 ①向量库故障时记忆永不丢（写向量失败仅降级，
 * 不回滚 DB）；②{@code get}/{@code delete}/{@code clear} 按 id 精确走 DB；③{@code search} 命中的 pk 回查 DB 拿完整
 * {@link MemoryRecord}（content 已随向量返回可省回查，但类型/时间戳等仍以 DB 为准）。
 *
 * <p><b>collection {@code nebula_memory} 约定</b>（见批次1 CollectionInitializer）：partitionKey={@code agent_code}，
 * 标量字段 {@code agent_code}/{@code user_id}/{@code conversation_id}/{@code mem_type}。故 {@code save} 的
 * {@link VectorRecord#scalars()} 填这四项；{@code search} 的 {@code agent_code} 走 {@link VectorQuery#partition()}
 * 分区裁剪，{@code user_id}/{@code mem_type}/{@code conversation_id} 拼进 {@code filterExpr}。
 *
 * @author nebula
 */
@Slf4j
public class VectorLongTermMemory implements LongTermMemory {

    /**
     * 被装饰的数据库版实现（真相源）
     */
    private final LongTermMemory delegate;
    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;
    private final AiProperties.Rag.Memory config;

    /**
     * 重索引标记器（批次4 对账）：向量写/删失败时把记忆行置 need_reindex=1，供对账任务补偿。惰性可选——
     * 取不到（如底层实现未实现 {@link VectorReindexMarker}）则退回纯 {@code log.warn}，对现有部署零破坏。
     */
    private final ObjectProvider<VectorReindexMarker> reindexMarkerProvider;

    public VectorLongTermMemory(LongTermMemory delegate,
                                VectorStore vectorStore,
                                EmbeddingProvider embeddingProvider,
                                AiProperties.Rag.Memory config,
                                ObjectProvider<VectorReindexMarker> reindexMarkerProvider) {
        this.delegate = delegate;
        this.vectorStore = vectorStore;
        this.embeddingProvider = embeddingProvider;
        this.config = config;
        this.reindexMarkerProvider = reindexMarkerProvider;
    }

    @Override
    public String save(MemoryRecord record) {
        // 先落 DB 拿到确定性主键（真相源）
        String id = delegate.save(record);
        if (id == null) {
            return null;
        }
        indexVector(id, record);
        return id;
    }

    @Override
    public List<String> saveAll(List<MemoryRecord> records) {
        List<String> ids = new ArrayList<>();
        if (records == null) {
            return ids;
        }
        for (MemoryRecord record : records) {
            String id = save(record);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public List<MemoryRecord> search(MemoryQuery query) {
        if (query == null) {
            return List.of();
        }
        // 无查询文本无法语义召回，回退 DB（按类型/会话过滤 + 时间倒序）
        if (!StringUtils.hasText(query.getText())) {
            return delegate.search(query);
        }
        List<float[]> vectors = embeddingProvider.embed(List.of(query.getText()));
        if (vectors.isEmpty()) {
            return delegate.search(query);
        }
        int topK = query.getTopK() > 0 ? query.getTopK() : config.getTopK();
        double minScore = query.getMinScore() != null ? query.getMinScore() : config.getMinScore();
        VectorQuery vq = new VectorQuery(
                MilvusCollections.MEMORY,
                query.getAgentCode(),
                vectors.get(0),
                topK,
                buildFilter(query),
                minScore);

        List<VectorMatch> matches;
        try {
            matches = vectorStore.search(vq);
        } catch (RuntimeException e) {
            // 向量检索故障降级：退回 DB 关键词检索，保证记忆仍可召回
            log.warn("记忆向量检索失败，降级 DB 检索: {}", e.getMessage());
            return delegate.search(query);
        }
        // 命中 pk 回查 DB 拿完整记录（保持类型/时间戳以 DB 为准），保序
        List<MemoryRecord> records = new ArrayList<>(matches.size());
        for (VectorMatch match : matches) {
            MemoryRecord record = delegate.get(query.getAgentCode(), query.getUserId(), match.pk());
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    @Override
    public MemoryRecord get(String agentCode, String userId, String id) {
        return delegate.get(agentCode, userId, id);
    }

    @Override
    public void delete(String agentCode, String userId, String id) {
        delegate.delete(agentCode, userId, id);
        try {
            vectorStore.delete(MilvusCollections.MEMORY, MilvusCollections.FIELD_PK + " == \"" + escape(id) + "\"");
        } catch (RuntimeException e) {
            // DB 已删，向量残留会成幽灵命中；置 need_reindex 交对账清理该 id 残留
            log.warn("记忆向量删除失败（DB 已删，置 need_reindex 待对账清残留）: id={}, {}", id, e.getMessage());
            markNeedReindexQuietly(id);
        }
    }

    @Override
    public void clear(String agentCode, String userId) {
        delegate.clear(agentCode, userId);
        StringBuilder filter = new StringBuilder("agent_code == \"").append(escape(agentCode)).append("\"");
        if (StringUtils.hasText(userId)) {
            filter.append(" && user_id == \"").append(escape(userId)).append("\"");
        }
        deleteVectorQuietly(filter.toString());
    }

    /**
     * 写入一条记忆的向量副本。向量写失败仅告警降级，不影响 DB（记忆不丢，代价是该条暂不可语义召回），
     * 并置 {@code need_reindex=1} 交由对账任务补偿。
     *
     * @param id     DB 主键（作 Milvus pk）
     * @param record 记忆条目
     */
    private void indexVector(String id, MemoryRecord record) {
        try {
            doUpsertVector(id, record);
        } catch (RuntimeException e) {
            log.warn("记忆向量写入失败（DB 已落，语义召回降级，置 need_reindex）: id={}, {}", id, e.getMessage());
            markNeedReindexQuietly(id);
        }
    }

    /**
     * 对账重索引：只重写向量、不碰 DB（真相源已在），成功返回 {@code true} 供对账任务清标记。
     * 与 {@link #indexVector} 的差异是「失败不再置位」（本就在处理置位行）且回传成功与否。
     *
     * @param record 记忆条目（含 id）
     * @return 向量补写成功
     */
    public boolean reindex(MemoryRecord record) {
        if (record == null || !StringUtils.hasText(record.getId())) {
            return false;
        }
        try {
            doUpsertVector(record.getId(), record);
            return true;
        } catch (RuntimeException e) {
            log.warn("记忆向量对账重写失败（保留 need_reindex 待下轮）: id={}, {}", record.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 组装并 upsert 一条记忆向量。异常向上抛由调用方决定降级/置位策略。空向量视为无需写入（正常返回）。
     */
    private void doUpsertVector(String id, MemoryRecord record) {
        List<float[]> vectors = embeddingProvider.embed(List.of(record.getContent()));
        if (vectors.isEmpty()) {
            return;
        }
        Map<String, Object> scalars = new LinkedHashMap<>();
        scalars.put("agent_code", record.getAgentCode());
        scalars.put("user_id", record.getUserId());
        scalars.put("conversation_id", record.getConversationId());
        scalars.put("mem_type", record.getType() == null ? null : record.getType().name());
        VectorRecord vr = new VectorRecord(id, vectors.get(0), record.getContent(), scalars, null);
        vectorStore.upsert(MilvusCollections.MEMORY, List.of(vr));
    }

    /**
     * 置 need_reindex 标记，标记器不可用时退回纯降级（不外抛，不影响主流程）。
     */
    private void markNeedReindexQuietly(String id) {
        VectorReindexMarker marker = reindexMarkerProvider.getIfAvailable();
        if (marker == null) {
            return;
        }
        try {
            marker.markNeedReindex(id);
        } catch (RuntimeException e) {
            log.warn("置 need_reindex 标记失败（对账将无法覆盖该条）: id={}, {}", id, e.getMessage());
        }
    }

    /**
     * 构造 Milvus 标量过滤表达式（agent_code 走 partition 不在此拼）。
     * user_id/conversation_id/mem_type 均自转义防注入。
     *
     * @param query 检索条件
     * @return 过滤表达式，无过滤项时返回 null
     */
    private String buildFilter(MemoryQuery query) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(query.getUserId())) {
            parts.add("user_id == \"" + escape(query.getUserId()) + "\"");
        }
        if (StringUtils.hasText(query.getConversationId())) {
            parts.add("conversation_id == \"" + escape(query.getConversationId()) + "\"");
        }
        MemoryType type = query.getType();
        if (type != null) {
            parts.add("mem_type == \"" + escape(type.name()) + "\"");
        }
        return parts.isEmpty() ? null : String.join(" && ", parts);
    }

    /**
     * 删除向量，失败仅告警（DB 已删，向量残留由批次4 对账清理）。
     */
    private void deleteVectorQuietly(String filterExpr) {
        try {
            vectorStore.delete(MilvusCollections.MEMORY, filterExpr);
        } catch (RuntimeException e) {
            log.warn("记忆向量删除失败（DB 已删，残留待对账）: filter={}, {}", filterExpr, e.getMessage());
        }
    }

    /**
     * 转义 Milvus 表达式字符串字面量中的双引号与反斜杠，防过滤表达式注入。
     */
    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
