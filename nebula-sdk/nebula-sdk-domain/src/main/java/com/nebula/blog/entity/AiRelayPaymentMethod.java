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
 * AI中转支付方式配置
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_payment_method")
public class AiRelayPaymentMethod implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 支付方式ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 支付方式编码（alipay/wechat/paypal/usdt等）
     */
    private String code;

    /**
     * 支付方式名称（支付宝/微信/PayPal/USDT等）
     */
    private String name;

    /**
     * 支付方式图标文件ID（sys_file）
     */
    private Long iconFileId;

    /**
     * 支付方式说明
     */
    private String description;

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
