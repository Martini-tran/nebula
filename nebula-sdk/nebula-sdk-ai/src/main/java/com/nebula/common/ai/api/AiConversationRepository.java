package com.nebula.common.ai.api;

import java.util.List;
import java.util.Map;

/**
 * AI对话记录接口
 *
 * @author nebula
 */
public interface AiConversationRepository {

    /**
     * 保存对话消息
     *
     * @param conversationId 对话ID
     * @param message        消息内容
     */
    void saveMessage(String conversationId, Map<String, Object> message);

    /**
     * 保存对话记录
     *
     * @param conversationId 对话ID
     * @param record         对话记录
     */
    void saveRecord(String conversationId, Map<String, Object> record);

    /**
     * 查询对话消息列表
     *
     * @param conversationId 对话ID
     * @return 消息列表
     */
    List<Map<String, Object>> listMessages(String conversationId);

    /**
     * 查询对话记录列表
     *
     * @param conversationId 对话ID
     * @return 对话记录列表
     */
    List<Map<String, Object>> listRecords(String conversationId);

    /**
     * 清空对话记录
     *
     * @param conversationId 对话ID
     */
    void clear(String conversationId);
}
