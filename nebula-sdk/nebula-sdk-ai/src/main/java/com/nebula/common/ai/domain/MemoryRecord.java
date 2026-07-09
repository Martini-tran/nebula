package com.nebula.common.ai.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/**
 * 长期记忆条目
 * 一条可被检索召回的记忆，按用户隔离，可选关联到具体会话。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class MemoryRecord {

    /**
     * 记忆ID（由存储实现生成或调用方指定）
     */
    private String id;

    /**
     * 归属Agent功能编码（记忆按Agent隔离，不同功能的Agent互不可见）
     */
    private String agentCode;

    /**
     * 归属用户ID
     */
    private String userId;

    /**
     * 关联会话ID（情景记忆常用，可为空表示跨会话）
     */
    private String conversationId;

    /**
     * 记忆类型
     */
    private MemoryType type;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 检索相关度得分（仅检索结果返回时有值，写入时为空）
     */
    private Double score;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 创建时间（毫秒时间戳）
     */
    private Long createdAt;

    /**
     * 更新时间（毫秒时间戳）
     */
    private Long updatedAt;
}
