package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 年度目标表（只存定义，进度按来源实时算）
 *
 * @author nebula
 */
@Data
@TableName("space_goal")
public class SpaceGoal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 年份
     */
    private Integer goalYear;

    /**
     * 目标
     */
    private String title;

    /**
     * 图标：Iconify 名称（集合:名字）
     */
    private String icon;

    /**
     * 类型：metric / milestone
     */
    private String kind;

    /**
     * 目标值（数值型）
     */
    private BigDecimal target;

    /**
     * 单位
     */
    private String unit;

    /**
     * 进度来源：habit / reading / ledger / task_list / manual
     */
    private String source;

    /**
     * 来源ID：习惯ID或任务清单ID
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceId;

    /**
     * 每条来源记录折算多少
     */
    private BigDecimal factor;

    /**
     * 开始用 Space 之前已有的量
     */
    private BigDecimal baseline;

    /**
     * 手动型的当前值
     */
    private BigDecimal manualValue;

    /**
     * 关键结果 JSON 数组
     */
    private String krs;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0否 1是
     */
    @TableLogic
    private Integer deleted;

    /**
     * 删除时间
     */
    private LocalDateTime deleteTime;
}
