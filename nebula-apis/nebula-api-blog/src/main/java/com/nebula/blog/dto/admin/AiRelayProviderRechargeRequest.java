package com.nebula.blog.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商充值记录 创建/更新请求
 */
@Data
public class AiRelayProviderRechargeRequest {

    /**
     * 服务商ID（必填）
     */
    private Long providerId;

    /**
     * 套餐ID（可选）
     */
    private Long packageId;

    /**
     * 充值金额（必填）
     */
    private BigDecimal amount;

    /**
     * 币种（默认CNY）
     */
    private String currency = "CNY";

    /**
     * 汇率（非CNY时折算汇率）
     */
    private BigDecimal exchangeRate;

    /**
     * 折合人民币金额
     */
    private BigDecimal cnyAmount;

    /**
     * 支付方式ID
     */
    private Long paymentMethodId;

    /**
     * 充值时间（必填）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rechargeTime;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 充值凭证文件ID
     */
    private Long voucherFileId;

    /**
     * 备注
     */
    private String remark;

    private Integer status = 1;
}
