package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转服务商创建请求
 */
@Data
public class AiRelayProviderCreateRequest {

    /**
     * 服务商名称（必填，最长 100）
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

    /**
     * 综合推荐分
     */
    private BigDecimal recommendScore;

    private Integer sortOrder = 0;

    /**
     * 状态（1正常 0下线），默认1
     */
    private Integer status = 1;
}
