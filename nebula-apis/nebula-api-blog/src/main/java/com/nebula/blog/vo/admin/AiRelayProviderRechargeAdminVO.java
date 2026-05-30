package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商充值记录管理VO
 */
@Data
public class AiRelayProviderRechargeAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    /**
     * 冗余服务商名称
     */
    private String providerName;

    private Long packageId;

    /**
     * 冗余套餐名称
     */
    private String packageName;

    private BigDecimal amount;

    private String currency;

    private BigDecimal exchangeRate;

    private BigDecimal cnyAmount;

    private Long paymentMethodId;

    /**
     * 冗余支付方式名称
     */
    private String paymentMethodName;

    private LocalDateTime rechargeTime;

    private String orderNo;

    private Long voucherFileId;

    private String voucherUrl;

    private String remark;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
