package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI中转服务商支持支付方式
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_provider_payment_method")
public class AiRelayProviderPaymentMethod implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务商ID（ai_relay_provider.id）
     */
    private Long providerId;

    /**
     * 支付方式ID（ai_relay_payment_method.id）
     */
    private Long paymentMethodId;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 展示排序
     */
    private Integer sortOrder;

    /**
     * 状态（1正常 0停用）
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
