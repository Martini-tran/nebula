package com.nebula.common.redis.util;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 通用工具类，统一封装常用操作。
 * 字符串值直接读写；对象走 JSON（{@link JsonMapper}）序列化。
 *
 * @author nebula
 */
public class RedisUtils {

    private final StringRedisTemplate template;
    private final JsonMapper jsonMapper;

    public RedisUtils(StringRedisTemplate template, JsonMapper jsonMapper) {
        this.template = template;
        this.jsonMapper = jsonMapper;
    }

    public StringRedisTemplate template() {
        return template;
    }

    // ===== string =====
    public void set(String key, String value) {
        template.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration ttl) {
        template.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return template.opsForValue().get(key);
    }

    public Boolean setIfAbsent(String key, String value, Duration ttl) {
        return template.opsForValue().setIfAbsent(key, value, ttl);
    }

    public String getAndDelete(String key) {
        return template.opsForValue().getAndDelete(key);
    }

    // ===== object via JSON =====
    public <T> void setObject(String key, T value) {
        try {
            template.opsForValue().set(key, jsonMapper.writeValueAsString(value));
        } catch (JacksonException e) {
            throw new IllegalStateException("redis serialize fail: key=" + key, e);
        }
    }

    public <T> void setObject(String key, T value, Duration ttl) {
        try {
            template.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
        } catch (JacksonException e) {
            throw new IllegalStateException("redis serialize fail: key=" + key, e);
        }
    }

    public <T> T getObject(String key, Class<T> type) {
        String raw = template.opsForValue().get(key);
        if (raw == null) {
            return null;
        }
        try {
            return jsonMapper.readValue(raw, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("redis deserialize fail: key=" + key, e);
        }
    }

    // ===== keys =====
    public Boolean delete(String key) {
        return template.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return template.delete(keys);
    }

    public Boolean expire(String key, Duration ttl) {
        return template.expire(key, ttl);
    }

    public Boolean hasKey(String key) {
        return template.hasKey(key);
    }

    public Set<String> keys(String pattern) {
        return template.keys(pattern);
    }

    // ===== hash =====
    public void hSet(String key, String field, String value) {
        template.opsForHash().put(key, field, value);
    }

    public void hSetAll(String key, Map<String, String> map) {
        template.opsForHash().putAll(key, map);
    }

    public String hGet(String key, String field) {
        Object v = template.opsForHash().get(key, field);
        return v == null ? null : v.toString();
    }

    public Map<String, String> hGetAll(String key) {
        Map<Object, Object> raw = template.opsForHash().entries(key);
        Map<String, String> out = new LinkedHashMap<>(raw.size());
        raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : v.toString()));
        return out;
    }

    public Long hDelete(String key, String... fields) {
        return template.opsForHash().delete(key, (Object[]) fields);
    }

    // ===== pub/sub =====
    public void publish(String channel, String message) {
        template.convertAndSend(channel, message);
    }
}
