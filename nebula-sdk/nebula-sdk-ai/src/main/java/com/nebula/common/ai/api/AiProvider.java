package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;

import java.util.Map;

/**
 * AI服务提供商接口
 *
 * @author nebula
 */
public interface AiProvider {

    /**
     * 获取服务提供商标识
     *
     * @return 服务提供商标识
     */
    String getProvider();

    /**
     * 判断是否支持指定服务提供商
     *
     * @param provider 服务提供商标识
     * @return 是否支持
     */
    default boolean supports(String provider) {
        return provider != null && provider.equalsIgnoreCase(getProvider());
    }

    /**
     * 发起对话调用
     *
     * @param request 请求参数
     * @return 响应结果
     */
    Map<String, Object> chat(AiRequest request);

    /**
     * 发起流式对话调用
     *
     * @param request  请求参数
     * @param callback 回调处理器
     */
    void stream(AiRequest request, AiCallback callback);
}
