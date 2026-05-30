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
 * AI中转服务商套餐
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_provider_package")
public class AiRelayProviderPackage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 套餐ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务商ID（ai_relay_provider.id）
     */
    private Long providerId;

    /**
     * 套餐类型ID（ai_relay_package_type.id）
     */
    private Long packageTypeId;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 套餐价格
     */
    private BigDecimal price;

    /**
     * 原价/划线价
     */
    private BigDecimal originalPrice;

    /**
     * 币种（CNY/USD等）
     */
    private String currency;

    /**
     * 是否推荐（1是 0否）
     */
    private Integer isRecommended;

    /**
     * 套餐推荐分
     */
    private BigDecimal recommendScore;

    /**
     * 套餐说明
     */
    private String description;

    /**
     * 展示排序
     */
    private Integer sortOrder;

    /**
     * 状态（1正常 0下线）
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
