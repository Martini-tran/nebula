package com.nebula.common.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * nebula-sdk-redis 配置属性
 *
 * @author nebula
 */
@ConfigurationProperties("nebula.redis")
public class NebulaRedisProperties {

    /**
     * 命名空间前缀（key/channel 都会加上）。
     * 显式配置后以此为准；不配置则 fallback 到 spring.application.name；再无则不加前缀。
     */
    private String namespace;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
