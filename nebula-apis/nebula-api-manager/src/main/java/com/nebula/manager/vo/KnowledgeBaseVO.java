package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库行（管理员端）
 *
 * @author nebula
 */
@Data
public class KnowledgeBaseVO {

    /**
     * 知识库ID
     */
    private Long id;

    /**
     * 知识库编码
     */
    private String kbCode;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * embedding 提供者编码
     */
    private String embeddingProvider;

    /**
     * embedding 模型名称
     */
    private String embeddingModel;

    /**
     * 向量维度
     */
    private Integer dimension;

    /**
     * 相似度度量
     */
    private String metric;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
