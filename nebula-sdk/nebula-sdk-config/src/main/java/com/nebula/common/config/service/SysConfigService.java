package com.nebula.common.config.service;

import java.util.Map;

/**
 * 公共配置读取门面
 * <ul>
 *     <li>所有 get* 方法均直读 Redis（{@link com.nebula.common.config.constant.ConfigConstants#REDIS_HASH_KEY}），sub-ms 级别</li>
 *     <li>refresh* 方法重读 DB → 覆写 Redis → 通过 Pub/Sub 通知所有实例</li>
 * </ul>
 *
 * @author nebula
 */
public interface SysConfigService {

    String get(String key);

    String getOrDefault(String key, String defaultValue);

    Integer getInt(String key, Integer defaultValue);

    Long getLong(String key, Long defaultValue);

    Boolean getBool(String key, Boolean defaultValue);

    <T> T getJson(String key, Class<T> type);

    /**
     * 按分组读取（直查 DB；适用于一次性批量加载场景）
     */
    Map<String, String> getGroup(String groupName);

    /**
     * 全量重载：读 DB → 覆写 Redis hash → 发布刷新通道
     */
    void refreshAll();

    /**
     * 单 key 重载：读 DB → 写/删 hash 字段 → 发布刷新通道
     */
    void refreshKey(String key);
}
