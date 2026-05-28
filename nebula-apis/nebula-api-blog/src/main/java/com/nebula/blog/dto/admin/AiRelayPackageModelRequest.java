package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转套餐模型 创建/更新请求
 */
@Data
public class AiRelayPackageModelRequest {

    /**
     * 模型ID（必填）
     */
    private Long modelId;

    /**
     * 服务商侧模型编码（最长 150）
     */
    private String providerModelCode;

    /**
     * 消耗倍率，默认1.0
     */
    private BigDecimal consumeMultiplier;

    private BigDecimal minChargeAmount;

    private Integer maxContextTokens;

    /**
     * 是否默认模型（1是 0否）
     */
    private Integer isDefault = 0;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
