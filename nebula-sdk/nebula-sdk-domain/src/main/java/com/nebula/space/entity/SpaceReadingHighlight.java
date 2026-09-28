package com.nebula.space.entity;

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
 * 稍后读划线表（删文章时一并删除）
 *
 * @author nebula
 */
@Data
@TableName("space_reading_highlight")
public class SpaceReadingHighlight implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 划线ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 文章ID
     */
    private Long itemId;

    /**
     * 定位锚点：第几段（从 0 开始）
     */
    private Integer para;

    /**
     * 定位锚点：段内起始字符（UTF-16 下标，含）
     */
    private Integer startOffset;

    /**
     * 定位锚点：段内结束字符（不含）
     */
    private Integer endOffset;

    /**
     * 划线原文
     */
    private String quote;

    /**
     * 颜色：yellow 观点 / blue 查证、待办
     */
    private String color;

    /**
     * 批注
     */
    private String note;

    /**
     * 转出的随手记ID
     */
    private Long noteId;

    /**
     * 转出的任务ID
     */
    private Long taskId;

    /**
     * 转出时的任务标题
     */
    private String taskTitle;

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
