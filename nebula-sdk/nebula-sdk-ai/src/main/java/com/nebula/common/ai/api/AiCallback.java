package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;

import java.util.Map;

/**
 * AI调用回调接口
 *
 * @author nebula
 */
public interface AiCallback {

    /**
     * 调用开始回调
     *
     * @param request 请求参数
     */
    default void onStart(AiRequest request) {
    }

    /**
     * 消息回调
     *
     * @param message 消息内容
     */
    default void onMessage(Map<String, Object> message) {
    }

    /**
     * 流式内容片段回调
     *
     * @param content 文本片段
     * @param chunk   原始片段数据
     */
    default void onDelta(String content, Map<String, Object> chunk) {
    }

    /**
     * 调用完成回调
     *
     * @param response 响应结果
     */
    default void onComplete(Map<String, Object> response) {
    }

    /**
     * 调用异常回调
     *
     * @param throwable 异常信息
     */
    default void onError(Throwable throwable) {
    }
}
