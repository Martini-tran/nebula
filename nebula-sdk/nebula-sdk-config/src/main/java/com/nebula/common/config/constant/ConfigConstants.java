package com.nebula.common.config.constant;

/**
 * 公共配置模块常量
 *
 * @author nebula
 */
public final class ConfigConstants {

    private ConfigConstants() {
    }

    /** Redis 中存放 sys_config 全量缓存的 hash key */
    public static final String REDIS_HASH_KEY = "nebula:config:cache";

    /** Redis Pub/Sub 通道：通知所有实例 sys_config 已变更 */
    public static final String REFRESH_CHANNEL = "nebula:config:refresh";

    /** 全量刷新事件载荷 */
    public static final String PAYLOAD_ALL = "ALL";
}
