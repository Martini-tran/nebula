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
 * 纪念日表（倒数日 / 每年纪念日 / 正数日）
 *
 * @author nebula
 */
@Data
@TableName("space_anniversary")
public class SpaceAnniversary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 纪念日ID
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
    private String title;

    /**
     * 图标：Iconify 名称（集合:名字）
     */
    private String icon;

    /**
     * 类型：countdown / annual / countup
     */
    private String annivType;

    /**
     * 公历日期
     */
    private LocalDate annivDate;

    /**
     * 历法：solar / lunar
     */
    private String calendar;

    /**
     * 农历月
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer lunarMonth;

    /**
     * 农历日
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer lunarDay;

    /**
     * 提前几天提醒，空为不提醒
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer remindDays;

    /**
     * 提醒时生成任务：0否 1是
     */
    private Integer createTask;

    /**
     * 生成任务的标题
     */
    private String taskTitle;

    /**
     * 已为哪一次生成过任务
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate taskFor;

    /**
     * 关联文件柜里的文件
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fileId;

    /**
     * 备注
     */
    private String note;

    /**
     * 标签
     */
    private String tag;

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
