package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 稍后读文章表（加入时抓取正文存档）
 *
 * @author nebula
 */
@Data
@TableName("space_reading")
public class SpaceReading implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 原网址
     */
    private String url;

    /**
     * 规范化网址的 SHA-256，同一用户同一网址只存一条
     */
    private String urlHash;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String excerpt;

    /**
     * 存档的阅读版正文，段落 JSON 数组；空为没抓到。划线按段落下标定位，存档后不再改
     */
    private String content;

    /**
     * 预计阅读分钟，0 为未知
     */
    private Integer readMinutes;

    /**
     * 状态：unread / reading / done
     */
    private String readStatus;

    /**
     * 读到的比例 0~1
     */
    private Double readProgress;

    /**
     * 上次读到的滚动位置（占全文高度的比例）
     */
    private Double readPosition;

    /**
     * 读后感
     */
    private String thought;

    /**
     * 是否归档：0否 1是
     */
    private Integer archived;

    /**
     * 从哪个书签加入
     */
    private Long bookmarkId;

    /**
     * 最后阅读时间
     */
    private LocalDateTime lastReadTime;

    /**
     * 读完时间；改回未读完时清空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime doneTime;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 加入时间
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
