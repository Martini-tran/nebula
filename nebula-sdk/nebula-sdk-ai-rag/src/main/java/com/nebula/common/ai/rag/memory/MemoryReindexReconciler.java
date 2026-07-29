package com.nebula.common.ai.rag.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.entity.AiMemory;
import com.nebula.common.ai.api.VectorReindexMarker;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.memory.store.AiMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 记忆向量对账任务（批次4 need_reindex 补偿）。
 *
 * <p>兑现「双写不一致靠对账兜底」这条设计承诺：{@link VectorLongTermMemory} 双写 DB + Milvus 时，向量侧失败会置
 * {@code need_reindex=1}（DB 真相源仍完整）。本任务定期扫描这些行，经 {@link VectorLongTermMemory#reindex} 补写向量，
 * 成功后清标记；失败保留待下轮。没有对账，向量坏账只增不减、召回静默劣化且无修复入口。
 *
 * <p><b>积压计数（批次4 可观测）</b>：每轮输出「待处理 / 成功 / 失败 / 剩余积压」——{@code need_reindex=1} 的行数
 * 即双写坏账积压量，是天然的健康指标，无需额外指标基建。
 *
 * <p><b>降级铁律</b>：对账是后台补偿，任何异常仅 {@code log}，绝不外抛（不影响记忆主流程）。仅当
 * {@code nebula.ai.rag.reconcile.enabled=true} 且向量装饰器就绪时装配（见 {@code RagReconcileAutoConfiguration}）。
 *
 * @author nebula
 */
@Slf4j
public class MemoryReindexReconciler {

    private final AiMemoryMapper aiMemoryMapper;
    private final VectorLongTermMemory vectorLongTermMemory;
    private final VectorReindexMarker reindexMarker;
    private final AiProperties.Rag.Reconcile config;

    public MemoryReindexReconciler(AiMemoryMapper aiMemoryMapper,
                                   VectorLongTermMemory vectorLongTermMemory,
                                   VectorReindexMarker reindexMarker,
                                   AiProperties.Rag.Reconcile config) {
        this.aiMemoryMapper = aiMemoryMapper;
        this.vectorLongTermMemory = vectorLongTermMemory;
        this.reindexMarker = reindexMarker;
        this.config = config;
    }

    /**
     * 定时对账。cron 由 {@code nebula.ai.rag.reconcile.cron} 配置（默认每 10 分钟）。
     */
    @Scheduled(cron = "${nebula.ai.rag.reconcile.cron:0 */10 * * * *}")
    public void reconcile() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            // 对账是后台补偿，整轮异常也不外抛
            log.warn("记忆向量对账整轮失败（下轮重试）: {}", e.getMessage());
        }
    }

    /**
     * 执行一轮对账：取一批 need_reindex=1 的行，逐条补写向量，成功清标记。
     *
     * @return 本轮成功补写条数
     */
    public int runOnce() {
        int batchSize = config.getBatchSize() > 0 ? config.getBatchSize() : 200;
        LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMemory::getNeedReindex, 1)
                .orderByAsc(AiMemory::getId)
                .last("limit " + batchSize);
        List<AiMemory> rows = aiMemoryMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return 0;
        }
        int success = 0;
        int failed = 0;
        for (AiMemory row : rows) {
            String id = String.valueOf(row.getId());
            if (vectorLongTermMemory.reindex(toRecord(row))) {
                reindexMarker.markReindexed(id);
                success++;
            } else {
                failed++;
            }
        }
        // 剩余积压 = 全表 need_reindex=1 的行数（含本轮未处理的 + 本轮失败仍置位的）
        long backlog = aiMemoryMapper.selectCount(
                new LambdaQueryWrapper<AiMemory>().eq(AiMemory::getNeedReindex, 1));
        log.info("记忆向量对账完成：本轮待处理={}，成功={}，失败={}，剩余积压={}",
                rows.size(), success, failed, backlog);
        return success;
    }

    /**
     * 实体转记忆记录（仅 reindex 所需字段：id/归属/类型/正文）。对账只重写向量，不回写 DB，故无需时间戳/元数据。
     */
    private MemoryRecord toRecord(AiMemory row) {
        return new MemoryRecord()
                .setId(String.valueOf(row.getId()))
                .setAgentCode(row.getAgentCode())
                .setUserId(row.getUserId())
                .setConversationId(row.getConversationId())
                .setType(parseType(row.getMemType()))
                .setContent(row.getContent());
    }

    private MemoryType parseType(String memType) {
        if (!StringUtils.hasText(memType)) {
            return null;
        }
        try {
            return MemoryType.valueOf(memType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
