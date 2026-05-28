package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转服务商套餐更新请求
 */
@Data
public class AiRelayProviderPackageUpdateRequest {

    private Long packageTypeId;

    /**
     * 套餐名称（最长 100）
     */
    private String name;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String currency;

    private Integer isRecommended;

    private BigDecimal recommendScore;

    /**
     * 套餐说明（最长 1000）
     */
    private String description;

    private Integer sortOrder;

    private Integer status;
}
