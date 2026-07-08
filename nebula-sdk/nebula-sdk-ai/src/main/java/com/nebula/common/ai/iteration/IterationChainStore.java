package com.nebula.common.ai.iteration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 迭代链存储（SPI）
 * 把跨实例迭代链的到点扫描与逐轮推进落库，对应 {@code ai_agent_iteration} 表。
 * SDK 仅定义接口；数据库实现由 {@code nebula-sdk-ai-flow} 的 store 包提供，缺省（无实现）时
 * {@link IterationDriver} 无链可推进——与 {@code AgentInstanceStore} 的分层完全一致。
 *
 * <p><b>幂等边界</b>：{@link #advance} 以 {@code (chainId, expectedSeq)} 做乐观锁断言，同一轮只推进一次
 * （配合 {@link IterationLock} 的 Redis 抢占，双保险防多实例重跑；见 docs/跨实例迭代层设计.md 5.2）。
 *
 * @author nebula
 */
public interface IterationChainStore {

    /**
     * 扫出到点应推进的链：{@code status='ACTIVE' AND next_run_at <= now}（走 idx_due 索引）。
     *
     * @param now   当前时间
     * @param limit 单次最多取多少条（防一次扫太多）
     * @return 到点链列表（不含大字段亦可，但至少含推进所需字段）
     */
    List<IterationChain> findDue(LocalDateTime now, int limit);

    /**
     * 按 chainId 读取链快照。
     *
     * @param chainId 链标识
     * @return 快照；不存在返回 null
     */
    IterationChain load(String chainId);

    /**
     * 推进一轮成功后回写链（幂等）：
     * <pre>
     * UPDATE ai_agent_iteration SET
     *   last_instance_id=?, seq=seq+1, next_run_at=?, status=?, consecutive_fails=0, lock_version=lock_version+1
     * WHERE chain_id=? AND seq=? AND status='ACTIVE';   -- 影响 0 行 = 已被别的节点推进过，放弃
     * </pre>
     *
     * @param chainId       链标识
     * @param expectedSeq   期望的当前 seq（= 本轮推进前的 seq），CAS 断言防重复推进
     * @param newInstanceId 本轮产出的实例 id（成为下一轮 carry-over 数据源）
     * @param nextRunAt     下一轮应触发时间（按 cron 算）
     * @param newStatus     推进后状态：ACTIVE（继续）| COMPLETED（出链条件满足/达上限）
     * @return 影响 1 行返回 true（本次推进生效）；0 行返回 false（已被别的节点推进，放弃）
     */
    boolean advance(String chainId, int expectedSeq, String newInstanceId,
                    LocalDateTime nextRunAt, String newStatus);

    /**
     * 推进一轮失败后回写链：{@code consecutive_fails+1}，达阈值置 PAUSED，并顺延 {@code next_run_at}（下轮重试）。
     * 不改 seq（本轮未成功不算已完成）。
     *
     * @param chainId          链标识
     * @param error            失败原因
     * @param pauseThreshold   连续失败达此值则自动 PAUSED（防定时打空转）
     * @param nextRetryAt      下一次重试时间
     */
    void markFailure(String chainId, String error, int pauseThreshold, LocalDateTime nextRetryAt);
}
