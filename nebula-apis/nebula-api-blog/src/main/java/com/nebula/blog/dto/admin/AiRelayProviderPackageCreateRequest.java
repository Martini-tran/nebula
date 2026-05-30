package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转服务商套餐创建请求
 */
@Data
public class AiRelayProviderPackageCreateRequest {

    /**
     * 服务商ID（必填）
     */
    private Long providerId;

    /**
     * 套餐类型ID（必填）
     */
    private Long packageTypeId;

    /**
     * 套餐名称（必填，最长 100）
     */
    private String name;

    /**
     * 套餐价格（必填）
     */
    private BigDecimal price;

    private BigDecimal originalPrice;

    private String currency = "CNY";

    /**
     * 是否推荐（1是 0否）
     */
    private Integer isRecommended = 0;

    /**
     * 套餐推荐分
     */
    private BigDecimal recommendScore;

    /**
     * 套餐说明（最长 1000）
     */
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
