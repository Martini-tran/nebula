package com.nebula.manager.dto;

import lombok.Data;

/**
 * 知识库保存请求（创建/更新）
 * 创建时必填 {@code kbCode}；维度/模型/度量留空则由服务端按 embedding 配置补齐。编码创建后不可变更。
 *
 * @author nebula
 */
@Data
public class KnowledgeBaseSaveRequest {

    /**
     * 知识库编码，全局唯一（创建必填，更新忽略）
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
     * embedding 提供者编码（留空取当前配置）
     */
    private String embeddingProvider;

    /**
     * embedding 模型名称（留空取当前配置）
     */
    private String embeddingModel;

    /**
     * 向量维度（留空取当前 embedding 维度）
     */
    private Integer dimension;

    /**
     * 相似度度量（留空默认 COSINE）
     */
    private String metric;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;
}
