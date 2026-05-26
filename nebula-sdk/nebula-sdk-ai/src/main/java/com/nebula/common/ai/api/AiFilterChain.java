package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;

import java.util.Map;

/**
 * AI调用过滤器链接口
 *
 * @author nebula
 */
public interface AiFilterChain {

    /**
     * 执行下一个过滤器或AI调用
     *
     * @param request 请求参数
     * @return 响应结果
     */
    Map<String, Object> doFilter(AiRequest request);
}
