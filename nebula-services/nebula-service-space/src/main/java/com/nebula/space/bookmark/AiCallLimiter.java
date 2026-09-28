package com.nebula.space.bookmark;

import com.nebula.common.redis.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 书签 AI 整理的调用次数限制：每人每小时 120 次（一次处理 30 条书签，够整理三千多条），防止模型费用失控
 */
@Component
@RequiredArgsConstructor
public class AiCallLimiter {

    static final int MAX_CALLS = 120;
    static final Duration WINDOW = Duration.ofHours(1);

    private final RedisUtils redis;

    /**
     * 记一次调用
     *
     * @return 超过上限返回 false
     */
    public boolean tryAcquire(Long userId) {
        String key = "space:bookmark-ai:" + userId;
        Long count = redis.template().opsForValue().increment(redis.key(key));
        if (count != null && count == 1) {
            redis.expire(key, WINDOW);
        }
        return count == null || count <= MAX_CALLS;
    }
}
