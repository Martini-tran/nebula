package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * AI模型 创建/更新请求
 */
@Data
public class AiRelayModelRequest {

    /**
     * 模型编码（必填，最长 100）
     */
    private String code;

    /**
     * 模型名称（必填，最长 100）
     */
    private String name;

    /**
     * 模型厂商（最长 50）
     */
    private String modelVendor;

    /**
     * 模型类型（必填，1文本 2图像 3音频 4多模态 5Embedding）
     */
    private Integer modelType;

    /**
     * 模型说明（最长 1000）
     */
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
