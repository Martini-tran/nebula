package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转套餐限制 创建/更新请求
 */
@Data
public class AiRelayProviderPackageLimitRequest {

    /**
     * 限制类型（1总额度 2每日额度 3每周额度 4每月额度 5单次额度）
     */
    @NotNull(message = "限制类型不能为空")
    private Integer limitType;

    @NotNull(message = "额度数量不能为空")
    private BigDecimal quotaAmount;

    @NotBlank(message = "额度单位不能为空")
    @Size(max = 50, message = "额度单位长度不能超过50")
    private String quotaUnit;

    /**
     * 重置周期（0不重置 1每日 2每周 3每月 4套餐周期）
     */
    private Integer resetCycle = 0;

    /**
     * 超限策略（1禁止使用 2按量计费 3限速）
     */
    private Integer overLimitStrategy = 1;

    @Size(max = 500, message = "限制说明长度不能超过500")
    private String description;

    private Integer status = 1;
}
