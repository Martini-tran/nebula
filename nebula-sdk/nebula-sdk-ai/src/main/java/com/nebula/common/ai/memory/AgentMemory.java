package com.nebula.common.ai.memory;

import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;

import java.util.List;
import java.util.Map;

/**
 * Agent记忆门面
 * 聚合单个Agent的短期记忆（会话历史）与长期记忆能力，按其{@link AgentMemoryConfig}配置工作，
 * 并自动以该Agent的功能编码隔离记忆。
 *
 * @author nebula
 */
public interface AgentMemory {

    /**
     * 获取Agent功能编码
     *
     * @return Agent功能编码
     */
    String agentCode();

    /**
     * 获取记忆配置
     *
     * @return 记忆配置
     */
    AgentMemoryConfig config();

    /**
     * 保存一条会话消息（短期记忆）
     *
     * @param conversationId 对话ID
     * @param message        消息内容
     */
    void saveMessage(String conversationId, Map<String, Object> message);

    /**
     * 加载会话历史（短期记忆，已按该Agent的窗口策略裁剪）
     *
     * @param conversationId 对话ID
     * @return 裁剪后的消息历史
     */
    List<Map<String, Object>> loadHistory(String conversationId);

    /**
     * 清空会话历史
     *
     * @param conversationId 对话ID
     */
    void clearConversation(String conversationId);

    /**
     * 写入一条长期记忆
     * 自动注入该Agent的功能编码；当未启用长期记忆或该记忆类型未启用时忽略并返回null。
     *
     * @param record 记忆条目
     * @return 记忆ID，未写入时返回null
     */
    String remember(MemoryRecord record);

    /**
     * 检索长期记忆
     * 自动注入该Agent的功能编码；未启用长期记忆时返回空列表。
     *
     * @param query 检索条件
     * @return 按相关度排序的记忆列表
     */
    List<MemoryRecord> recall(MemoryQuery query);

    /**
     * 删除一条长期记忆
     *
     * @param userId 归属用户ID
     * @param id     记忆ID
     */
    void forget(String userId, String id);
}
