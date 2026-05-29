package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商个人充值记录（用于佐证推荐真实性）
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_provider_recharge")
public class AiRelayProviderRecharge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 充值记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务商ID（ai_relay_provider.id）
     */
    private Long providerId;

    /**
     * 套餐ID（ai_relay_provider_package.id），按量计费/直充可空
     */
    private Long packageId;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 币种（CNY/USD等）
     */
    private String currency;

    /**
     * 汇率（非CNY时折算汇率）
     */
    private BigDecimal exchangeRate;

    /**
     * 折合人民币金额
     */
    private BigDecimal cnyAmount;

    /**
     * 支付方式ID（ai_relay_payment_method.id）
     */
    private Long paymentMethodId;

    /**
     * 充值时间
     */
    private LocalDateTime rechargeTime;

    /**
     * 订单号/交易流水号
     */
    private String orderNo;

    /**
     * 充值凭证文件ID（sys_file）
     */
    private Long voucherFileId;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 状态（1正常 0作废）
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
