package com.nebula.common.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nebula.common.config.constant.ConfigConstants;
import com.nebula.common.config.event.SysConfigChangedEvent;
import com.nebula.common.config.mapper.SysConfigMapper;
import com.nebula.common.config.service.SysConfigService;
import com.nebula.common.redis.util.RedisUtils;
import com.nebula.system.entity.SysConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper mapper;
    private final RedisUtils redis;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher events;

    public SysConfigServiceImpl(SysConfigMapper mapper,
                                RedisUtils redis,
                                @Qualifier("redisJsonMapper") JsonMapper jsonMapper,
                                ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.events = events;
    }

    @Override
    public String get(String key) {
        if (key == null) {
            return null;
        }
        return redis.hGet(ConfigConstants.REDIS_HASH_KEY, key);
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public Integer getInt(String key, Integer defaultValue) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Long getLong(String key, Long defaultValue) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Boolean getBool(String key, Boolean defaultValue) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        String s = v.trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("0".equals(s) || "false".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s) || "off".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return defaultValue;
    }

    @Override
    public <T> T getJson(String key, Class<T> type) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(v, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("sys_config json parse fail: key=" + key, e);
        }
    }

    @Override
    public Map<String, String> getGroup(String groupName) {
        List<SysConfig> rows = mapper.selectList(new QueryWrapper<SysConfig>().eq("group_name", groupName));
        Map<String, String> out = new LinkedHashMap<>();
        for (SysConfig c : rows) {
            out.put(c.getConfigKey(), c.getConfigValue() == null ? "" : c.getConfigValue());
        }
        return out;
    }

    @Override
    public void refreshAll() {
        applyAllFromDb();
        redis.publish(ConfigConstants.REFRESH_CHANNEL, ConfigConstants.PAYLOAD_ALL);
        events.publishEvent(new SysConfigChangedEvent(this, ConfigConstants.PAYLOAD_ALL));
    }

    @Override
    public void refreshKey(String key) {
        applyKeyFromDb(key);
        redis.publish(ConfigConstants.REFRESH_CHANNEL, key);
        events.publishEvent(new SysConfigChangedEvent(this, key));
    }

    /**
     * 内部：仅做 DB → Redis 同步，不发广播。供启动加载器和监听器复用。
     */
    public int applyAllFromDb() {
        List<SysConfig> rows = mapper.selectList(null);
        Map<String, String> map = new HashMap<>(rows.size() * 2);
        for (SysConfig c : rows) {
            map.put(c.getConfigKey(), c.getConfigValue() == null ? "" : c.getConfigValue());
        }
        redis.delete(ConfigConstants.REDIS_HASH_KEY);
        if (!map.isEmpty()) {
            redis.hSetAll(ConfigConstants.REDIS_HASH_KEY, map);
        }
        return map.size();
    }

    public void applyKeyFromDb(String key) {
        SysConfig c = mapper.selectOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (c == null) {
            redis.hDelete(ConfigConstants.REDIS_HASH_KEY, key);
        } else {
            redis.hSet(ConfigConstants.REDIS_HASH_KEY, key, c.getConfigValue() == null ? "" : c.getConfigValue());
        }
    }
}
