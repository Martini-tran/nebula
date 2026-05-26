package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;

import java.util.Map;

/**
 * AI调用过滤器接口
 *
 * @author nebula
 */
public interface AiFilter {

    /**
     * 过滤AI调用
     *
     * @param request 请求参数
     * @param chain   过滤器链
     * @return 响应结果
     */
    Map<String, Object> filter(AiRequest request, AiFilterChain chain);

    /**
     * 获取过滤器顺序
     *
     * @return 顺序值
     */
    default int getOrder() {
        return 0;
    }
}
