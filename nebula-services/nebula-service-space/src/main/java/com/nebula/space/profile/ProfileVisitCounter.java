package com.nebula.space.profile;

import com.nebula.common.redis.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 公开主页的近 30 天访问量：每人一个 Redis 哈希，按天一个字段，读的时候顺手删掉 30 天前的
 *
 * <p>只是给主人看的数字，Redis 出错不影响访客打开主页，读不到就当 0。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileVisitCounter {

    static final int DAYS = 30;

    private final RedisUtils redis;

    public void hit(Long userId, LocalDate today) {
        try {
            redis.template().opsForHash().increment(redis.key(key(userId)), today.toString(), 1);
            // 一个月没人来就整个过期
            redis.expire(key(userId), Duration.ofDays(DAYS + 1L));
        } catch (RuntimeException e) {
            log.warn("公开主页访问计数失败 userId={}: {}", userId, e.getMessage());
        }
    }

    public int recent(Long userId, LocalDate today) {
        Map<String, String> days;
        try {
            days = redis.hGetAll(key(userId));
        } catch (RuntimeException e) {
            log.warn("读取公开主页访问计数失败 userId={}: {}", userId, e.getMessage());
            return 0;
        }
        LocalDate from = today.minusDays(DAYS - 1L);
        int total = 0;
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> e : days.entrySet()) {
            try {
                if (LocalDate.parse(e.getKey()).isBefore(from)) {
                    stale.add(e.getKey());
                } else {
                    total += Integer.parseInt(e.getValue());
                }
            } catch (DateTimeParseException | NumberFormatException ex) {
                stale.add(e.getKey());
            }
        }
        if (!stale.isEmpty()) {
            try {
                redis.hDelete(key(userId), stale.toArray(String[]::new));
            } catch (RuntimeException e) {
                // 下次再删
            }
        }
        return total;
    }

    private static String key(Long userId) {
        return "space:profile:visits:" + userId;
    }
}
