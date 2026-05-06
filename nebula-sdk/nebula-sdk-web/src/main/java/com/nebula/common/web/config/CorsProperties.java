package com.nebula.common.web.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨域配置属性
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.cors")
public class CorsProperties {

    /**
     * 是否启用跨域过滤器
     */
    private boolean enabled = true;

    /**
     * 生效的路径模式
     */
    private String pathPattern = "/**";

    /**
     * 允许的源模式（支持 *、通配符）；与 allowCredentials=true 兼容
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

    /**
     * 允许的请求头
     */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    /**
     * 允许的请求方法
     */
    private List<String> allowedMethods = new ArrayList<>(List.of("*"));

    /**
     * 暴露给浏览器的响应头
     */
    private List<String> exposedHeaders = new ArrayList<>();

    /**
     * 是否允许携带 Cookie
     */
    private boolean allowCredentials = true;

    /**
     * 预检请求缓存时间（秒）
     */
    private long maxAge = 3600L;
}
