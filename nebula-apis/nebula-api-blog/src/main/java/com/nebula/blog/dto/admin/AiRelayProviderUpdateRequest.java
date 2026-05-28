package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转服务商更新请求
 */
@Data
public class AiRelayProviderUpdateRequest {

    /**
     * 服务商名称（最长 100）
     */
    private String name;

    /**
     * 官网地址（最长 255）
     */
    private String websiteUrl;

    private Long logoFileId;

    /**
     * 服务商简介（最长 1000）
     */
    private String description;

    private BigDecimal recommendScore;

    private Integer sortOrder;

    private Integer status;
}
