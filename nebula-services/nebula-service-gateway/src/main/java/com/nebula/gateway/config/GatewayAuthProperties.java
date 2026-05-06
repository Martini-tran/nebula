package com.nebula.gateway.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关鉴权相关配置
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.gateway.auth")
public class GatewayAuthProperties {

    /**
     * 鉴权白名单（Ant 风格），命中后网关直接放行
     */
    private List<String> whitelist = new ArrayList<>();
}