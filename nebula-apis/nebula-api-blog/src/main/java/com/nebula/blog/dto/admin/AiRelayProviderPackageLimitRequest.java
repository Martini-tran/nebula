package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转套餐限制 创建/更新请求
 */
@Data
public class AiRelayProviderPackageLimitRequest {

    /**
     * 限制类型（必填，1总额度 2每日额度 3每周额度 4每月额度 5单次额度）
     */
    private Integer limitType;

    /**
     * 额度数量（必填）
     */
    private BigDecimal quotaAmount;

    /**
     * 额度单位（必填，最长 50，如 token/request/credit）
     */
    private String quotaUnit;

    /**
     * 重置周期（0不重置 1每日 2每周 3每月 4套餐周期）
     */
    private Integer resetCycle = 0;

    /**
     * 超限策略（1禁止使用 2按量计费 3限速）
     */
    private Integer overLimitStrategy = 1;

    /**
     * 限制说明（最长 500）
     */
    private String description;

    private Integer status = 1;
}
