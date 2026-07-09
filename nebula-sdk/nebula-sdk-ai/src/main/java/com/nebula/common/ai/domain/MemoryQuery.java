package com.nebula.common.ai.domain;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 长期记忆检索条件
 * 语义中立：{@code text}表示检索意图，底层用向量语义检索或关键字检索由实现决定。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class MemoryQuery {

    /**
     * 归属Agent功能编码（必填，记忆按Agent隔离）
     */
    private String agentCode;

    /**
     * 归属用户ID（必填，记忆按用户隔离）
     */
    private String userId;

    /**
     * 关联会话ID（可选，用于将检索限定在某次会话）
     */
    private String conversationId;

    /**
     * 记忆类型过滤（可选，为空表示不限类型）
     */
    private MemoryType type;

    /**
     * 检索文本/意图
     */
    private String text;

    /**
     * 返回的最大条数
     */
    private int topK = 5;

    /**
     * 相关度得分下限（可选，为空表示不过滤）
     */
    private Double minScore;
}
