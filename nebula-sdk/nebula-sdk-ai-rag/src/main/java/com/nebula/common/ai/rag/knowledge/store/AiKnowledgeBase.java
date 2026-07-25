package com.nebula.common.ai.rag.knowledge.store;

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
 * 知识库表
 * 一个知识库 = 一组同维度、同 embedding 模型的文档集合，由 {@code kb_code} 唯一标识，被 {@code knowledge_search}
 * 工具与导入流水线引用。建库时落 {@code embedding_provider}/{@code embedding_model}/{@code dimension}/{@code metric}，
 * 读写前据此做维度校验（防模型漂移导致的脏写）。
 *
 * @author nebula
 */
@Data
@TableName("ai_knowledge_base")
public class AiKnowledgeBase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 知识库编码，全局唯一，被工具/导入引用
     */
    private String kbCode;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * embedding 提供者编码（对应 {@code EmbeddingProvider.code()}）
     */
    private String embeddingProvider;

    /**
     * embedding 模型名称
     */
    private String embeddingModel;

    /**
     * 向量维度（须与 embedding 模型一致）
     */
    private Integer dimension;

    /**
     * 相似度度量（COSINE/L2/IP）
     */
    private String metric;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

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
