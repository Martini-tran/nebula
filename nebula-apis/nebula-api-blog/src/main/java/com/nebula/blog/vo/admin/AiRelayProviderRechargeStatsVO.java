package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商充值汇总VO（按服务商聚合）
 */
@Data
public class AiRelayProviderRechargeStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long providerId;

    private String providerName;

    /**
     * 充值次数
     */
    private Integer rechargeCount;

    /**
     * 累计折合人民币金额
     */
    private BigDecimal totalCnyAmount;

    /**
     * 最近一次充值时间
     */
    private LocalDateTime lastRechargeTime;
}
