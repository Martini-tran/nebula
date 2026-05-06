package com.nebula.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 网关基础组件装配
 *
 * @author nebula
 */
@Configuration
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewayConfig {

    /**
     * 用于调用 auth 服务的 WebClient（独立 bean，避免与 LB-WebClient 冲突）
     */
    @Bean
    public WebClient authWebClient(GatewayAuthProperties props) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(props.getTimeoutMillis()));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}