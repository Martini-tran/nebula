package com.nebula.common.captcha.cache;

import com.anji.captcha.service.CaptchaCacheService;
import com.nebula.common.redis.util.RedisUtils;
import java.time.Duration;

/**
 * AJ-Captcha 的 Redis 缓存适配器。
 * 通过 SPI 注册：META-INF/services/com.anji.captcha.service.CaptchaCacheService
 * AJ-Captcha 启动时根据 captcha.cache.type=redis 从 SPI 列表中匹配 type()=="redis" 的实现。
 *
 * @author nebula
 */
public class RedisCaptchaCacheService implements CaptchaCacheService {

    /**
     * AJ-Captcha 的 SPI 是无参构造，运行期由我们的 AutoConfig 在 Bean 创建后注入实例。
     */
    private static volatile RedisUtils redis;

    public static void bind(RedisUtils redisUtils) {
        redis = redisUtils;
    }

    private RedisUtils redis() {
        RedisUtils r = redis;
        if (r == null) {
            throw new IllegalStateException("RedisCaptchaCacheService is not bound yet, "
                    + "ensure CaptchaAutoConfiguration runs before AJ-Captcha factory init");
        }
        return r;
    }

    @Override
    public void set(String key, String value, long expiresInSeconds) {
        redis().set(key, value, Duration.ofSeconds(expiresInSeconds));
    }

    @Override
    public boolean exists(String key) {
        Boolean has = redis().hasKey(key);
        return Boolean.TRUE.equals(has);
    }

    @Override
    public void delete(String key) {
        redis().delete(key);
    }

    @Override
    public String get(String key) {
        return redis().get(key);
    }

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public Long increment(String key, long val) {
        RedisUtils r = redis();
        return r.template().opsForValue().increment(r.key(key), val);
    }


    public void setExpire(String key, long seconds) {
        redis().expire(key, Duration.ofSeconds(seconds));
    }
}
