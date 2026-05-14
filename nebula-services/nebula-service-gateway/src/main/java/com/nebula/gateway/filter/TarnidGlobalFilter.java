package com.nebula.gateway.filter;

import com.nebula.common.core.constant.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪 ID 过滤器
 * 若请求头中已携带 X-Tarnid 则透传，否则生成新的 UUID
 * 写入下游请求头，并回写到响应头
 *
 * @author nebula
 */
@Slf4j
@Component
public class TarnidGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tarnid = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_TARNID);
        if (tarnid == null || tarnid.isBlank()) {
            tarnid = UUID.randomUUID().toString().replace("-", "");
        }

        final String finalTarnid = tarnid;
        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.header(SecurityConstants.HEADER_TARNID, finalTarnid))
                .build();

        return chain.filter(mutated)
                .then(Mono.fromRunnable(() ->
                        mutated.getResponse().getHeaders().set(SecurityConstants.HEADER_TARNID, finalTarnid)
                ));
    }
}
