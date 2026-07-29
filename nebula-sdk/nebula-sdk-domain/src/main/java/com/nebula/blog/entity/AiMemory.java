package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI长期记忆表
 * 跨会话、可检索的长期记忆条目，按 agent_code + user_id 隔离。
 *
 * @author nebula
 */
@Data
@TableName("ai_memory")
public class AiMemory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记忆ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属Agent功能编码
     */
    private String agentCode;

    /**
     * 归属用户ID
     */
    private String userId;

    /**
     * 关联会话ID（可空=跨会话）
     */
    private String conversationId;

    /**
     * 记忆类型：EPISODIC/SEMANTIC/PROCEDURAL/ENTITY
     */
    private String memType;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 扩展元数据（JSON文本）
     */
    private String metadata;

    /**
     * 向量待重索引标记：0=向量已同步，1=向量写入/删除失败待对账补偿（批次4 need_reindex 对账）
     */
    private Integer needReindex;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
