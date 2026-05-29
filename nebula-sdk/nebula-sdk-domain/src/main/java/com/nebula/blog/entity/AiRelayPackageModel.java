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
 * AI中转套餐支持模型及消耗倍率
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_package_model")
public class AiRelayPackageModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 套餐模型ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 套餐ID（ai_relay_provider_package.id）
     */
    private Long packageId;

    /**
     * 模型ID（ai_relay_model.id）
     */
    private Long modelId;

    /**
     * 服务商侧模型编码，不填则默认使用模型编码
     */
    private String providerModelCode;

    /**
     * 消耗倍率，如1.5表示消耗额度*1.5
     */
    private BigDecimal consumeMultiplier;

    /**
     * 最低扣费额度
     */
    private BigDecimal minChargeAmount;

    /**
     * 最大上下文Token数
     */
    private Integer maxContextTokens;

    /**
     * 输入Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用
     */
    private BigDecimal inputPricePerMillionTokens;

    /**
     * 输出Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用
     */
    private BigDecimal outputPricePerMillionTokens;

    /**
     * 是否默认模型（1是 0否）
     */
    private Integer isDefault;

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
