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
 * 专注记录表：一轮番茄钟结束（完成或放弃）时写一条，写入后不再修改
 *
 * @author nebula
 */
@Data
@TableName("space_focus_session")
public class SpaceFocusSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 专注记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 关联任务ID，不挂任务为空
     */
    private Long taskId;

    /**
     * 任务标题快照
     */
    private String taskTitle;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime endedAt;

    /**
     * 计划分钟
     */
    private Integer plannedMin;

    /**
     * 实际分钟（扣除暂停）
     */
    private Integer actualMin;

    /**
     * 状态：done / abandoned
     */
    private String status;

    /**
     * 打断次数
     */
    private Integer interruptions;

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
