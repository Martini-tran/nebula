package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 旅行游记主表
 *
 * @author nebula
 */
@Data
@TableName("travel_trip")
public class TravelTrip implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 游记ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 作者用户ID（关联sys_user）
     */
    private Long userId;

    /**
     * 标题（如：云南七日自由行）
     */
    private String title;

    /**
     * URL标识
     */
    private String slug;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 封面图（关联blog_file_asset）
     */
    private Long coverFileId;

    /**
     * 状态：draft/published/archived
     */
    private String status;

    /**
     * 可见性：public/private
     */
    private String visibility;

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    /**
     * 总天数
     */
    private Integer daysCount;

    /**
     * 人数
     */
    private Integer persons;

    /**
     * 总花费
     */
    private BigDecimal costTotal;

    /**
     * 货币（CNY/USD等）
     */
    private String costCurrency;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 点赞次数
     */
    private Integer likeCount;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

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