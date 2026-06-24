package com.nebula.common.ai.api;

import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;

import java.util.List;

/**
 * 长期记忆接口
 * 与{@link AiConversationRepository}（短期会话历史）相对，负责跨会话、可检索的长期记忆。
 * 接口语义中立，底层可由向量库、检索引擎或数据库实现。
 *
 * @author nebula
 */
public interface LongTermMemory {

    /**
     * 保存一条记忆
     *
     * @param record 记忆条目
     * @return 记忆ID
     */
    String save(MemoryRecord record);

    /**
     * 批量保存记忆
     *
     * @param records 记忆条目列表
     * @return 记忆ID列表
     */
    List<String> saveAll(List<MemoryRecord> records);

    /**
     * 检索相关记忆
     *
     * @param query 检索条件
     * @return 按相关度排序的记忆列表
     */
    List<MemoryRecord> search(MemoryQuery query);

    /**
     * 根据ID获取记忆
     *
     * @param agentCode 归属Agent功能编码
     * @param userId    归属用户ID
     * @param id        记忆ID
     * @return 记忆条目，不存在时返回null
     */
    MemoryRecord get(String agentCode, String userId, String id);

    /**
     * 根据ID删除记忆
     *
     * @param agentCode 归属Agent功能编码
     * @param userId    归属用户ID
     * @param id        记忆ID
     */
    void delete(String agentCode, String userId, String id);

    /**
     * 清空指定Agent下指定用户的全部长期记忆
     *
     * @param agentCode 归属Agent功能编码
     * @param userId    归属用户ID
     */
    void clear(String agentCode, String userId);
}
