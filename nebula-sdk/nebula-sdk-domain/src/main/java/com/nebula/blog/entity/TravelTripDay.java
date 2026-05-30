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
 * 行程日表
 *
 * @author nebula
 */
@Data
@TableName("travel_trip_day")
public class TravelTripDay implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 行程日ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属游记ID
     */
    private Long tripId;

    /**
     * 第几天（从1开始）
     */
    private Integer dayNumber;

    /**
     * 当日标题（如：抵达大理，漫步古城）
     */
    private String title;

    /**
     * 当日描述
     */
    private String description;

    /**
     * 住宿地点
     */
    private String accommodation;

    /**
     * 餐饮花费
     */
    private BigDecimal mealCost;

    /**
     * 交通花费
     */
    private BigDecimal transportCost;

    /**
     * 其他花费
     */
    private BigDecimal otherCost;

    /**
     * 排序序号
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