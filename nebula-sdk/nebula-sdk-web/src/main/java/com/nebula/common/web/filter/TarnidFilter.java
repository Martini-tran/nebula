package com.nebula.common.web.filter;

import com.nebula.common.core.constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪 ID 过滤器
 * 若请求头中已携带 X-Tarnid 则透传，否则生成新的 UUID
 * 写入 MDC（key: tarnid）供日志输出，同时写入响应头
 *
 * @author nebula
 */
public class TarnidFilter extends OncePerRequestFilter implements Ordered {

    private static final String MDC_KEY = "tarnid";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tarnid = request.getHeader(SecurityConstants.HEADER_TARNID);
        if (tarnid == null || tarnid.isBlank()) {
            tarnid = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, tarnid);
        response.setHeader(SecurityConstants.HEADER_TARNID, tarnid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
