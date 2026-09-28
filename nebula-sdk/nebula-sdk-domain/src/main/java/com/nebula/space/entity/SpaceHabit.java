package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 习惯表（频率、提醒时间以 JSON 存本表）
 *
 * @author nebula
 */
@Data
@TableName("space_habit")
public class SpaceHabit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 习惯ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 名称
     */
    private String name;

    /**
     * 图标：Iconify 名称（集合:名字）
     */
    private String icon;

    /**
     * 类型：check 勾选 / count 计数 / duration 时长
     */
    private String kind;

    /**
     * 目标值：勾选为 1，计数为次数，时长为分钟
     */
    private Integer target;

    /**
     * 计数单位
     */
    private String unit;

    /**
     * 频率 JSON
     */
    private String freq;

    /**
     * 提醒时间 JSON 数组
     */
    private String reminders;

    /**
     * 时长型：专注记录自动累加进来
     */
    private Integer fromFocus;

    /**
     * 已归档：0否 1是
     */
    private Integer archived;

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
