package com.nebula.manager.config;

import com.nebula.common.ai.iteration.IterationLock;
import com.nebula.common.redis.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 迭代链推进锁的 Redis 实现（多实例部署防重跑）
 * 用 {@link RedisUtils#setIfAbsent}（{@code SET NX EX}）抢占本轮推进权：多个 manager 节点同时扫到同一条到点的链时，
 * 只有抢到锁的节点推进（见 docs/跨实例迭代层设计.md 5.1）。装配后覆盖 SDK 缺省的 {@link IterationLock#NOOP}。
 *
 * <p><b>过期兜底</b>：持锁节点跑一半崩了，key 到 ttl 自动失效；链的 {@code next_run_at} 未推进 → 下次扫描重新到点、
 * 换节点重跑（at-least-once）。释放时校验持有者，避免误删他人锁（本节点跑超 ttl 后锁已被别人拿到的极端情况）。
 *
 * @author nebula
 */
@Component
@RequiredArgsConstructor
public class RedisIterationLock implements IterationLock {

    private static final String KEY_PREFIX = "ai:iter:lock:";

    /**
     * 本节点标识：进程内随机一次，用于释放时校验持有者（避免误删）
     */
    private final String owner = java.util.UUID.randomUUID().toString();

    private final RedisUtils redisUtils;

    @Override
    public boolean tryAcquire(String chainId, Duration ttl) {
        Boolean ok = redisUtils.setIfAbsent(KEY_PREFIX + chainId, owner, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void release(String chainId) {
        String key = KEY_PREFIX + chainId;
        // 只释放自己持有的锁：值等于 owner 才删（跑超 ttl 后锁可能已易主，不能误删）
        if (owner.equals(redisUtils.get(key))) {
            redisUtils.delete(key);
        }
    }
}
