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
 * AI中转套餐类型配置
 *
 * @author nebula
 */
@Data
@TableName("ai_relay_package_type")
public class AiRelayPackageType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 套餐类型ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 套餐类型编码（day/week/month/usage等）
     */
    private String code;

    /**
     * 套餐类型名称（天卡/周卡/月卡/按量）
     */
    private String name;

    /**
     * 计费模式（1固定周期 2按量计费）
     */
    private Integer billingMode;

    /**
     * 套餐周期数值，如1、7、30，按量计费可为空
     */
    private Integer durationValue;

    /**
     * 周期单位（1天 2周 3月 4年），按量计费可为空
     */
    private Integer durationUnit;

    /**
     * 类型说明
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
