package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.ai.iteration.IterationChain;
import com.nebula.common.ai.iteration.IterationChainStore;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库版迭代链存储
 * 以 MyBatis-Plus 把跨实例迭代链落 {@code ai_agent_iteration} 表，实现 SDK 的 {@link IterationChainStore}。
 * 与 {@link DatabaseAgentInstanceStore} 同构：由 {@link AiFlowStoreAutoConfiguration} 以
 * {@code @ConditionalOnMissingBean} 装配。JSON 列（carryOver/seedInputs）经 {@link FlowJsonCodec}。
 *
 * <p><b>幂等核心</b>：{@link #advance} 以 {@code WHERE chain_id=? AND seq=? AND status='ACTIVE'} 做 CAS，
 * 影响 0 行即已被别的节点推进过（多实例双保险，见 docs/跨实例迭代层设计.md 5.2）。
 *
 * @author nebula
 */
@Slf4j
public class DatabaseIterationChainStore implements IterationChainStore {

    private final AiAgentIterationMapper mapper;

    public DatabaseIterationChainStore(AiAgentIterationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<IterationChain> findDue(LocalDateTime now, int limit) {
        List<AiAgentIteration> rows = mapper.selectList(new LambdaQueryWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getStatus, "ACTIVE")
                .le(AiAgentIteration::getNextRunAt, now)
                .orderByAsc(AiAgentIteration::getNextRunAt)
                .last("limit " + Math.max(1, limit)));
        return rows.stream().map(this::toChain).toList();
    }

    @Override
    public IterationChain load(String chainId) {
        AiAgentIteration row = findByChainId(chainId);
        return row == null ? null : toChain(row);
    }

    @Override
    public boolean advance(String chainId, int expectedSeq, String newInstanceId,
                           LocalDateTime nextRunAt, String newStatus) {
        // CAS：只有 status='ACTIVE' 且 seq 仍等于 expectedSeq 才推进，防同一轮被多节点重复推进
        LambdaUpdateWrapper<AiAgentIteration> cas = new LambdaUpdateWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getChainId, chainId)
                .eq(AiAgentIteration::getStatus, "ACTIVE")
                .eq(AiAgentIteration::getSeq, expectedSeq)
                .set(AiAgentIteration::getLastInstanceId, newInstanceId)
                .set(AiAgentIteration::getSeq, expectedSeq + 1)
                .set(AiAgentIteration::getNextRunAt, nextRunAt)
                .set(AiAgentIteration::getStatus, newStatus)
                .set(AiAgentIteration::getConsecutiveFails, 0)
                .set(AiAgentIteration::getErrorMsg, null)
                .setSql("lock_version = lock_version + 1");
        int affected = mapper.update(null, cas);
        if (affected == 0) {
            log.warn("链[{}]advance CAS 未命中（expectedSeq={} 已被推进或状态已变）", chainId, expectedSeq);
        }
        return affected == 1;
    }

    @Override
    public void markFailure(String chainId, String error, int pauseThreshold, LocalDateTime nextRetryAt) {
        AiAgentIteration row = findByChainId(chainId);
        if (row == null) {
            log.warn("记链失败找不到链[{}]", chainId);
            return;
        }
        int fails = (row.getConsecutiveFails() == null ? 0 : row.getConsecutiveFails()) + 1;
        LambdaUpdateWrapper<AiAgentIteration> upd = new LambdaUpdateWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getChainId, chainId)
                .set(AiAgentIteration::getConsecutiveFails, fails)
                .set(AiAgentIteration::getErrorMsg, truncate(error))
                .set(AiAgentIteration::getNextRunAt, nextRetryAt);
        // 连续失败达阈值 → 自动 PAUSED，防定时空转（人工介入再 resume）
        if (fails >= pauseThreshold) {
            upd.set(AiAgentIteration::getStatus, "PAUSED");
            log.warn("链[{}]连续失败 {} 次达阈值，自动 PAUSED", chainId, fails);
        }
        mapper.update(null, upd);
    }

    /* ===================== 内部 ===================== */

    private IterationChain toChain(AiAgentIteration r) {
        return new IterationChain(
                r.getChainId(),
                r.getName(),
                r.getAgentCode(),
                r.getCron(),
                r.getSeq() == null ? 0 : r.getSeq(),
                r.getMaxIterations(),
                r.getUntilExpr(),
                FlowJsonCodec.readStringMap(r.getCarryOver()),
                FlowJsonCodec.readObjectMap(r.getSeedInputs()),
                r.getLastInstanceId(),
                r.getUserId(),
                r.getConversationId(),
                r.getStatus(),
                r.getLockVersion() == null ? 0 : r.getLockVersion(),
                r.getWebhookUrl());
    }

    private AiAgentIteration findByChainId(String chainId) {
        if (chainId == null || chainId.isBlank()) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<AiAgentIteration>()
                .eq(AiAgentIteration::getChainId, chainId)
                .last("limit 1"));
    }

    /**
     * error_msg 是 TEXT，但避免超长栈塞爆，截断到合理长度
     */
    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}
