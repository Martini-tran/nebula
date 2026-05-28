package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转套餐模型 创建/更新请求
 */
@Data
public class AiRelayPackageModelRequest {

    @NotNull(message = "模型ID不能为空")
    private Long modelId;

    @Size(max = 150, message = "服务商侧模型编码长度不能超过150")
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
