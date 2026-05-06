package com.nebula.gateway.filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.domain.R;
import com.nebula.common.core.enums.ResultCode;
import com.nebula.gateway.config.GatewayAuthProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局鉴权过滤器：
 * - 命中白名单直接放行
 * - 其余请求通过 sa-token reactor + 共享 Redis 校验登录态（StpUtil.checkLogin）
 * - 校验通过后将 X-User-Id 透传到下游业务服务
 *
 * @author nebula
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayAuthProperties props;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthGlobalFilter(GatewayAuthProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (isWhitelisted(path)) {
            return chain.filter(stripInternalHeaders(exchange));
        }

        // 通过 SaReactorSyncHolder 把 exchange 注入 sa-token 反应式上下文，使 StpUtil 可同步使用
        try {
            SaReactorSyncHolder.setContext(exchange);
            StpUtil.checkLogin();
            String userId = StpUtil.getLoginIdAsString();
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
        } catch (NotLoginException e) {
            return unauthorized(exchange, mapNotLoginCode(e));
        } catch (Exception e) {
            log.warn("鉴权异常 path={}, msg={}", path, e.getMessage());
            return unauthorized(exchange, ResultCode.TOKEN_INVALID);
        } finally {
            SaReactorSyncHolder.clearContext();
        }
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

    private ResultCode mapNotLoginCode(NotLoginException e) {
        return switch (e.getType()) {
            case NotLoginException.NOT_TOKEN, NotLoginException.INVALID_TOKEN -> ResultCode.TOKEN_INVALID;
            case NotLoginException.TOKEN_TIMEOUT -> ResultCode.TOKEN_EXPIRED;
            default -> ResultCode.UNAUTHORIZED;
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        R<Void> body = R.fail(code);
        // 错误体由固定枚举生成，无特殊字符；手动拼接 JSON 避免引入额外序列化依赖
        String json = "{\"code\":" + body.getCode()
                + ",\"message\":\"" + body.getMessage()
                + "\",\"data\":null}";
        DataBufferFactory factory = response.bufferFactory();
        DataBuffer buffer = factory.wrap(json.getBytes(StandardCharsets.UTF_8));
        response.getHeaders().setContentLength(buffer.readableByteCount());
        return response.writeWith(Mono.just(buffer));
    }
}