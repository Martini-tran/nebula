package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * AI中转服务商优势 创建/更新请求
 */
@Data
public class AiRelayProviderAdvantageRequest {

    /**
     * 优势标题（必填，最长 100）
     */
    private String title;

    /**
     * 优势说明（最长 500）
     */
    private String content;

    /**
     * 优势类型（1普通优势 2核心优势 3风险提示）
     */
    private Integer advantageType = 1;

    private Long iconFileId;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
