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
import java.time.LocalDateTime;

/**
 * 行程打卡点（每日具体地点）
 *
 * @author nebula
 */
@Data
@TableName("travel_checkin")
public class TravelCheckin implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 打卡点ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属行程日ID
     */
    private Long tripDayId;

    /**
     * 关联的目的地ID（可选，若为自定义地点则为NULL）
     */
    private Long destinationId;

    /**
     * 自定义地点名称（当destination_id为空时使用）
     */
    private String customName;

    /**
     * 自定义经纬度
     */
    private String customLocation;

    /**
     * 到达时间
     */
    private LocalDateTime arrivalTime;

    /**
     * 离开时间
     */
    private LocalDateTime departureTime;

    /**
     * 游玩笔记
     */
    private String notes;

    /**
     * 个人评分
     */
    private BigDecimal rating;

    /**
     * 照片ID数组（存储blog_file_asset的ID列表）
     */
    private String photos;

    /**
     * 当日内的顺序
     */
    private Integer sortOrder;

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