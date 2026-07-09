package com.nebula.common.ai.iteration;

import java.time.Duration;

/**
 * 迭代链推进锁（SPI）
 * 多实例部署下，每个节点的 {@link IterationDriver} 都会扫到同一条到点的链——用本锁保证<b>同一轮只有一个节点推进</b>
 * （见 docs/跨实例迭代层设计.md 5.1）。SDK 只定义接口，把 Redis 依赖挡在 {@code nebula-sdk-ai} 之外：
 *
 * <ul>
 *   <li><b>单实例部署</b>：无需实现，用默认的 {@link #NOOP}（永远抢占成功，靠 advance 乐观锁兜幂等）。</li>
 *   <li><b>多实例部署</b>：由能拿到 {@code RedisUtils} 的业务模块提供 Redis 实现（{@code SET NX EX} 抢占 + 过期兜底）。</li>
 * </ul>
 *
 * <p><b>过期兜底</b>：持锁节点跑一半崩了，锁到期自动释放；链的 {@code next_run_at} 未推进 → 下次扫描重新到点、
 * 换节点重跑。因每轮实例已落库、链靠 {@code last_instance_id} 只认最后一次成功，重跑不串错（at-least-once）。
 *
 * @author nebula
 */
public interface IterationLock {

    /**
     * 尝试抢占某条链本轮的推进权。
     *
     * @param chainId 链标识
     * @param ttl     锁的持有上限（超时自动释放，兜底崩溃）
     * @return 抢占成功 true；已被别的节点持有 false（本次放弃推进）
     */
    boolean tryAcquire(String chainId, Duration ttl);

    /**
     * 释放锁（推进结束后调用；即便不调，ttl 到期也会自动释放）。
     *
     * @param chainId 链标识
     */
    void release(String chainId);

    /**
     * 单实例默认实现：永远抢占成功、释放无操作。多实例时须替换为 Redis 实现。
     */
    IterationLock NOOP = new IterationLock() {
        @Override
        public boolean tryAcquire(String chainId, Duration ttl) {
            return true;
        }

        @Override
        public void release(String chainId) {
            // no-op
        }
    };
}
