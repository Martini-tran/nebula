package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转套餐模型 管理VO
 */
@Data
public class AiRelayPackageModelAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long packageId;

    private Long modelId;

    /**
     * 模型编码
     */
    private String modelCode;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型厂商
     */
    private String modelVendor;

    private String providerModelCode;

    private BigDecimal consumeMultiplier;

    private BigDecimal minChargeAmount;

    private Integer maxContextTokens;

    private Integer isDefault;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
