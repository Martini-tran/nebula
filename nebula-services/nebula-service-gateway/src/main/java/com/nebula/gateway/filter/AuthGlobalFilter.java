package com.nebula.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.domain.R;
import com.nebula.common.core.enums.ResultCode;
import com.nebula.gateway.config.GatewayAuthProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局鉴权过滤器：白名单放行；其余请求调用 nebula-service-auth /session 校验登录态，
 * 校验通过后将用户信息透传到下游服务。
 *
 * @author nebula
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayAuthProperties props;
    private final WebClient authWebClient;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthGlobalFilter(GatewayAuthProperties props,
                            WebClient authWebClient,
                            ObjectMapper objectMapper) {
        this.props = props;
        this.authWebClient = authWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        if (isWhitelisted(path)) {
            return chain.filter(stripInternalHeaders(exchange));
        }

        String token = request.getHeaders().getFirst(SecurityConstants.HEADER_AUTHORIZATION);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
        }

        return authWebClient.get()
                .uri(props.getSessionUri())
                .header(SecurityConstants.HEADER_AUTHORIZATION, token)
                .retrieve()
                .bodyToMono(SessionPayload.class)
                .flatMap(payload -> {
                    if (payload == null || payload.data == null) {
                        return unauthorized(exchange, ResultCode.TOKEN_INVALID);
                    }
                    Object loginFlag = payload.data.getOrDefault("login", payload.data.get("isLogin"));
                    Object loginId = payload.data.get("loginId");
                    if (!Boolean.TRUE.equals(loginFlag) || loginId == null) {
                        return unauthorized(exchange, ResultCode.TOKEN_INVALID);
                    }
                    String userId = String.valueOf(loginId);
                    ServerWebExchange mutated = exchange.mutate()
                            .request(builder -> builder.headers(headers -> {
                                headers.remove(SecurityConstants.HEADER_USER_ID);
                                headers.remove(SecurityConstants.HEADER_USER_NAME);
                                headers.remove(SecurityConstants.HEADER_USER_ROLES);
                                headers.remove(SecurityConstants.HEADER_USER_PERMS);
                                headers.remove(SecurityConstants.HEADER_INNER_CALL);
                                headers.set(SecurityConstants.HEADER_USER_ID, userId);
                            }))
                            .build();
                    return chain.filter(mutated);
                })
                .onErrorResume(ex -> {
                    log.warn("调用 auth /session 失败: {}", ex.getMessage());
                    return unauthorized(exchange, ResultCode.TOKEN_INVALID);
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitelisted(String path) {
        List<String> whitelist = props.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String pattern : whitelist) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 客户端禁止伪造内部 header，统一在入口剥离
     */
    private ServerWebExchange stripInternalHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(SecurityConstants.HEADER_USER_ID);
                    headers.remove(SecurityConstants.HEADER_USER_NAME);
                    headers.remove(SecurityConstants.HEADER_USER_ROLES);
                    headers.remove(SecurityConstants.HEADER_USER_PERMS);
                    headers.remove(SecurityConstants.HEADER_INNER_CALL);
                }))
                .build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(R.fail(code));
        } catch (JsonProcessingException ex) {
            bytes = ("{\"code\":" + code.getCode() + ",\"message\":\"" + code.getMessage() + "\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBufferFactory factory = response.bufferFactory();
        DataBuffer buffer = factory.wrap(bytes);
        response.getHeaders().setContentLength(buffer.readableByteCount());
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 与 auth /session 返回的统一响应结构对齐：
     * { code, message, data: { isLogin, loginId, tokenTimeout } }
     * Jackson 反序列化时 "isLogin" 会以 "login" 作为 property 名。
     */
    private static class SessionPayload {
        public Integer code;
        public String message;
        public Map<String, Object> data;
    }
}