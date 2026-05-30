package com.nebula.gateway.filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.nebula.common.core.constant.SecurityConstants;
import com.nebula.common.core.domain.R;
import com.nebula.common.core.enums.ResultCode;
import com.nebula.gateway.config.GatewayAuthProperties;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
 * Global authentication filter.
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

        try {
            SaReactorSyncHolder.setContext(exchange);
            StpUtil.checkLogin();
            String userId = StpUtil.getLoginIdAsString();
            List<String> roles = loadSessionList(userId, SaSession.ROLE_LIST);
            List<String> permissions = loadSessionList(userId, SaSession.PERMISSION_LIST);
            ServerWebExchange mutated = exchange.mutate()
                    .request(builder -> builder.headers(headers -> {
                        headers.remove(SecurityConstants.HEADER_USER_ID);
                        headers.remove(SecurityConstants.HEADER_USER_NAME);
                        headers.remove(SecurityConstants.HEADER_USER_ROLES);
                        headers.remove(SecurityConstants.HEADER_USER_PERMS);
                        headers.remove(SecurityConstants.HEADER_INNER_CALL);
                        headers.set(SecurityConstants.HEADER_USER_ID, userId);
                        if (!roles.isEmpty()) {
                            headers.set(SecurityConstants.HEADER_USER_ROLES, String.join(",", roles));
                        }
                        if (!permissions.isEmpty()) {
                            headers.set(SecurityConstants.HEADER_USER_PERMS, String.join(",", permissions));
                        }
                    }))
                    .build();
            return chain.filter(mutated);
        } catch (NotLoginException e) {
            return unauthorized(exchange, mapNotLoginCode(e));
        } catch (Exception e) {
            log.warn("Gateway authentication failed, path: {}, msg: {}", path, e.getMessage());
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

    private List<String> loadSessionList(String loginId, String key) {
        try {
            return toStringList(StpUtil.getSessionByLoginId(loginId).get(key));
        } catch (Exception e) {
            log.debug("Read Sa-Token session auth data failed, loginId: {}, key: {}, msg: {}",
                    loginId, key, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            return java.util.stream.IntStream.range(0, length)
                    .mapToObj(i -> Array.get(value, i))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        return Arrays.stream(value.toString().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

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
        String json = "{\"code\":" + body.getCode()
                + ",\"message\":\"" + body.getMessage()
                + "\",\"data\":null}";
        DataBufferFactory factory = response.bufferFactory();
        DataBuffer buffer = factory.wrap(json.getBytes(StandardCharsets.UTF_8));
        response.getHeaders().setContentLength(buffer.readableByteCount());
        return response.writeWith(Mono.just(buffer));
    }
}
