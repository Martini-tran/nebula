package com.nebula.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 网关基础组件装配
 *
 * @author nebula
 */
@Configuration
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewayConfig {
}