package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.AiRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI服务接口
 * 负责根据请求中的服务提供商选择对应的 {@link AiProvider} 执行调用
 *
 * @author nebula
 */
public interface AiService {

    /**
     * 发起对话调用
     * 调用时会执行已配置的 {@link AiFilter}
     *
     * @param request 请求参数
     * @return 响应结果
     */
    Map<String, Object> chat(AiRequest request);

    /**
     * 异步发起对话调用
     *
     * @param request 请求参数
     * @return 异步响应结果
     */
    CompletableFuture<Map<String, Object>> chatAsync(AiRequest request);

    /**
     * 发起流式对话调用
     *
     * @param request  请求参数
     * @param callback 回调处理器
     */
    void stream(AiRequest request, AiCallback callback);

    /**
     * 保存对话消息
     *
     * @param conversationId 对话ID
     * @param message        消息内容
     */
    void saveMessage(String conversationId, Map<String, Object> message);

    /**
     * 查询对话消息列表
     *
     * @param conversationId 对话ID
     * @return 消息列表
     */
    List<Map<String, Object>> listMessages(String conversationId);

    /**
     * 记录AI调用日志
     *
     * @param record 日志记录
     */
    void log(Map<String, Object> record);

    /**
     * 清空对话记录
     *
     * @param conversationId 对话ID
     */
    void clearConversation(String conversationId);
}
