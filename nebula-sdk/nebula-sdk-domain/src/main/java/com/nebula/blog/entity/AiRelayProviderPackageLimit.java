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
 * AI中转服务商套餐额度限制
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_provider_package_limit")
public class AiRelayProviderPackageLimit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 套餐限制ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 套餐ID（ai_relay_provider_package.id）
     */
    private Long packageId;

    /**
     * 限制类型（1总额度 2每日额度 3每周额度 4每月额度 5单次额度）
     */
    private Integer limitType;

    /**
     * 额度数量
     */
    private BigDecimal quotaAmount;

    /**
     * 额度单位（token/request/credit等）
     */
    private String quotaUnit;

    /**
     * 重置周期（0不重置 1每日 2每周 3每月 4套餐周期）
     */
    private Integer resetCycle;

    /**
     * 超限策略（1禁止使用 2按量计费 3限速）
     */
    private Integer overLimitStrategy;

    /**
     * 限制说明
     */
    private String description;

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
