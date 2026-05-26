package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI客户端接口
 *
 * @author nebula
 */
public interface AiClient {

    /**
     * 发起对话调用
     *
     * @param request 请求参数
     * @return 响应结果
     */
    Map<String, Object> chat(AiRequest request);

    /**
     * 发起简单文本对话调用
     *
     * @param prompt 提示词
     * @return 响应结果
     */
    default Map<String, Object> chat(String prompt) {
        return chat(new AiRequest(prompt));
    }

    /**
     * 基于消息列表发起对话调用
     *
     * @param messages 消息列表
     * @return 响应结果
     */
    default Map<String, Object> chat(List<Map<String, Object>> messages) {
        return chat(new AiRequest(messages));
    }

    /**
     * 异步发起对话调用
     *
     * @param request 请求参数
     * @return 异步响应结果
     */
    default CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    /**
     * 发起流式对话调用
     *
     * @param request  请求参数
     * @param callback 回调处理器
     */
    void stream(AiRequest request, AiCallback callback);
}
