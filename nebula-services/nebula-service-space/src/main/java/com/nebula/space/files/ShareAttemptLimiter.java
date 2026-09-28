package com.nebula.space.files;

import com.nebula.common.redis.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 分享链接的提取码试错限制：同一个链接 15 分钟内错 10 次就先锁住，防止挨个试
 */
@Component
@RequiredArgsConstructor
public class ShareAttemptLimiter {

    static final int MAX_FAILS = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final RedisUtils redis;

    public boolean locked(String code) {
        String count = redis.get(key(code));
        return count != null && Long.parseLong(count) >= MAX_FAILS;
    }

    public void fail(String code) {
        Long count = redis.template().opsForValue().increment(redis.key(key(code)));
        if (count != null && count == 1) {
            redis.expire(key(code), WINDOW);
        }
    }

    public void clear(String code) {
        redis.delete(key(code));
    }

    private static String key(String code) {
        return "space:share:fail:" + code;
    }
}
