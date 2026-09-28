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
 * 会议记录表（参会人、议程、已同步任务以 JSON 存本表）
 *
 * @author nebula
 */
@Data
@TableName("space_meeting")
public class SpaceMeeting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会议ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 日期
     */
    private LocalDate meetingDate;

    /**
     * 开始时间 HH:mm
     */
    private String startTime;

    /**
     * 时长（分钟）
     */
    private Integer durationMin;

    /**
     * 模板
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String template;

    /**
     * 参会人 JSON 数组
     */
    private String attendees;

    /**
     * 议程 JSON 数组
     */
    private String agenda;

    /**
     * 记录中的当前议题
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentAgendaId;

    /**
     * 正文 Markdown
     */
    private String content;

    /**
     * 状态：planned / live / done
     */
    private String status;

    /**
     * 开始记录时间
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime endedAt;

    /**
     * 已同步进任务的待办 JSON：待办文本 → 任务ID
     */
    private String syncedTasks;

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
