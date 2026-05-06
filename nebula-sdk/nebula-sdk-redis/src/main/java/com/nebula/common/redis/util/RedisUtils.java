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

    /**
     * Redis模板实例
     * 用于执行各种Redis操作，如字符串、哈希、列表等
     */
    private final StringRedisTemplate template;
    
    /**
     * JSON映射器实例
     * 用于对象与JSON字符串之间的序列化和反序列化
     */
    private final JsonMapper jsonMapper;

    /**
     * 构造函数
     * 初始化Redis工具类，注入必要的依赖
     * 
     * @param template Redis字符串模板，用于执行Redis操作
     * @param jsonMapper JSON映射器，用于对象序列化
     */
    public RedisUtils(StringRedisTemplate template, JsonMapper jsonMapper) {
        this.template = template;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 获取Redis模板实例
     * 用于执行底层Redis操作
     * 
     * @return Redis字符串模板实例
     */
    public StringRedisTemplate template() {
        return template;
    }

    // ===== string 操作 =====

    /**
     * 设置字符串值
     * 将指定的key-value对存入Redis
     * 
     * @param key 键
     * @param value 值
     */
    public void set(String key, String value) {
        template.opsForValue().set(key, value);
    }

    /**
     * 设置字符串值并指定过期时间
     * 将指定的key-value对存入Redis，并设置过期时间
     * 
     * @param key 键
     * @param value 值
     * @param ttl 过期时间
     */
    public void set(String key, String value, Duration ttl) {
        template.opsForValue().set(key, value, ttl);
    }

    /**
     * 获取字符串值
     * 从Redis中获取指定键对应的值
     * 
     * @param key 键
     * @return 对应的值，如果不存在则返回null
     */
    public String get(String key) {
        return template.opsForValue().get(key);
    }

    /**
     * 设置键值对（仅当键不存在时）
     * 只有当键不存在时才设置值，常用于分布式锁等场景
     * 
     * @param key 键
     * @param value 值
     * @param ttl 过期时间
     * @return 是否设置成功，true表示设置成功，false表示键已存在
     */
    public Boolean setIfAbsent(String key, String value, Duration ttl) {
        return template.opsForValue().setIfAbsent(key, value, ttl);
    }

    /**
     * 获取并删除键值对
     * 获取指定键的值后立即删除该键值对
     * 
     * @param key 键
     * @return 键对应的值，如果不存在则返回null
     */
    public String getAndDelete(String key) {
        return template.opsForValue().getAndDelete(key);
    }

    // ===== object via JSON 操作 =====

    /**
     * 设置对象值（通过JSON序列化）
     * 将对象序列化为JSON字符串后存入Redis
     * 
     * @param key 键
     * @param value 要存储的对象
     * @param <T> 对象类型
     */
    public <T> void setObject(String key, T value) {
        try {
            template.opsForValue().set(key, jsonMapper.writeValueAsString(value));
        } catch (JacksonException e) {
            throw new IllegalStateException("redis serialize fail: key=" + key, e);
        }
    }

    /**
     * 设置对象值并指定过期时间（通过JSON序列化）
     * 将对象序列化为JSON字符串后存入Redis，并设置过期时间
     * 
     * @param key 键
     * @param value 要存储的对象
     * @param ttl 过期时间
     * @param <T> 对象类型
     */
    public <T> void setObject(String key, T value, Duration ttl) {
        try {
            template.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
        } catch (JacksonException e) {
            throw new IllegalStateException("redis serialize fail: key=" + key, e);
        }
    }

    /**
     * 获取对象值（通过JSON反序列化）
     * 从Redis中获取JSON字符串并反序列化为指定类型的对象
     * 
     * @param key 键
     * @param type 目标对象类型
     * @param <T> 对象类型
     * @return 反序列化后的对象，如果不存在则返回null
     */
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

    // ===== keys 操作 =====

    /**
     * 删除指定键
     * 从Redis中删除指定的键值对
     * 
     * @param key 要删除的键
     * @return 是否删除成功，true表示删除成功，false表示键不存在
     */
    public Boolean delete(String key) {
        return template.delete(key);
    }

    /**
     * 删除多个键
     * 从Redis中批量删除指定的键值对
     * 
     * @param keys 要删除的键集合
     * @return 成功删除的键的数量
     */
    public Long delete(Collection<String> keys) {
        return template.delete(keys);
    }

    /**
     * 设置键的过期时间
     * 为指定的键设置过期时间
     * 
     * @param key 键
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    public Boolean expire(String key, Duration ttl) {
        return template.expire(key, ttl);
    }

    /**
     * 判断键是否存在
     * 检查指定的键是否存在于Redis中
     * 
     * @param key 键
     * @return 键是否存在，true表示存在，false表示不存在
     */
    public Boolean hasKey(String key) {
        return template.hasKey(key);
    }

    /**
     * 查找匹配模式的键
     * 根据模式查找Redis中所有匹配的键
     * 
     * @param pattern 键的匹配模式，如"user:*"匹配所有以"user:"开头的键
     * @return 匹配的键集合
     */
    public Set<String> keys(String pattern) {
        return template.keys(pattern);
    }

    // ===== hash 操作 =====

    /**
     * 设置哈希表中的字段值
     * 在指定的哈希表中设置字段和值
     * 
     * @param key 哈希表的键
     * @param field 哈希表中的字段
     * @param value 字段对应的值
     */
    public void hSet(String key, String field, String value) {
        template.opsForHash().put(key, field, value);
    }

    /**
     * 设置哈希表中的多个字段值
     * 在指定的哈希表中批量设置多个字段和值
     * 
     * @param key 哈希表的键
     * @param map 包含字段和值的映射
     */
    public void hSetAll(String key, Map<String, String> map) {
        template.opsForHash().putAll(key, map);
    }

    /**
     * 获取哈希表中的字段值
     * 从指定的哈希表中获取字段对应的值
     * 
     * @param key 哈希表的键
     * @param field 哈希表中的字段
     * @return 字段对应的值，如果不存在则返回null
     */
    public String hGet(String key, String field) {
        Object v = template.opsForHash().get(key, field);
        return v == null ? null : v.toString();
    }

    /**
     * 获取哈希表中的所有字段和值
     * 获取指定哈希表中的所有字段和值的映射
     * 
     * @param key 哈希表的键
     * @return 包含所有字段和值的映射
     */
    public Map<String, String> hGetAll(String key) {
        Map<Object, Object> raw = template.opsForHash().entries(key);
        Map<String, String> out = new LinkedHashMap<>(raw.size());
        raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : v.toString()));
        return out;
    }

    /**
     * 删除哈希表中的一个或多个字段
     * 从指定的哈希表中删除一个或多个字段
     * 
     * @param key 哈希表的键
     * @param fields 要删除的字段数组
     * @return 成功删除的字段数量
     */
    public Long hDelete(String key, String... fields) {
        return template.opsForHash().delete(key, (Object[]) fields);
    }

    // ===== pub/sub 发布订阅操作 =====

    /**
     * 发布消息到指定频道
     * 向Redis频道发布消息，供订阅者接收
     * 
     * @param channel 频道名称
     * @param message 要发布的消息内容
     */
    public void publish(String channel, String message) {
        template.convertAndSend(channel, message);
    }
}
