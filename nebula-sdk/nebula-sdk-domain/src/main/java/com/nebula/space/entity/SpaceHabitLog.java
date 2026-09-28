package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 习惯打卡表：同一习惯同一天一条，取消打卡直接物理删除（没有逻辑删除列，唯一键才不冲突）
 *
 * @author nebula
 */
@Data
@TableName("space_habit_log")
public class SpaceHabitLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 打卡ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 习惯ID
     */
    private Long habitId;

    /**
     * 打卡日期
     */
    private LocalDate logDate;

    /**
     * 当天的值
     */
    private Integer value;

    /**
     * 备注
     */
    private String note;

    /**
     * 事后补打卡：0否 1是
     */
    private Integer backfilled;

    /**
     * 最近一次写入时间
     */
    private LocalDateTime logTime;

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
}
