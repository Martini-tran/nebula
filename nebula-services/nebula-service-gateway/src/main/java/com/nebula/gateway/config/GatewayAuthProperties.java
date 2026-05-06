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
     * 调用 auth 服务校验会话的完整地址
     */
    private String sessionUri = "http://localhost:9001/auth/session";

    /**
     * 调用 auth /session 的超时时间（毫秒）
     */
    private long timeoutMillis = 3000L;

    /**
     * 鉴权白名单（Ant 风格），命中后网关直接放行
     */
    private List<String> whitelist = new ArrayList<>();
}