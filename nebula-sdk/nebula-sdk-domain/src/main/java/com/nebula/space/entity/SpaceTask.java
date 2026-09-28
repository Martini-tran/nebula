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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务表（子任务、重复规则以 JSON 存本表）
 *
 * @author nebula
 */
@Data
@TableName("space_task")
public class SpaceTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 所属清单ID，空为无清单
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long listId;

    /**
     * 标题
     */
    private String title;

    /**
     * 日期，空为收件箱
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;

    /**
     * 时间 HH:mm，空为全天
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dueTime;

    /**
     * 优先级：3高 2中 1低 0无
     */
    private Integer priority;

    /**
     * 已完成：0否 1是
     */
    private Integer done;

    /**
     * 完成时间
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime doneTime;

    /**
     * 预估分钟数
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer estimateMin;

    /**
     * 提前多少分钟提醒，空不提醒
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer remindBefore;

    /**
     * 重复规则 JSON
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String repeatRule;

    /**
     * 子任务 JSON 数组
     */
    private String subtasks;

    /**
     * 来源类型：note/meeting/bookmark/reading/person
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceType;

    /**
     * 来源ID
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceId;

    /**
     * 来源名称
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceLabel;

    /**
     * 备注
     */
    private String note;

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
