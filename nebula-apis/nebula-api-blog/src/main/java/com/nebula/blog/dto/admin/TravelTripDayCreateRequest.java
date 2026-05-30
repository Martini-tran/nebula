package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 行程日创建请求
 */
@Data
public class TravelTripDayCreateRequest {

    /**
     * 所属游记ID
     */
    private Long tripId;

    /**
     * 第几天（从1开始）
     */
    private Integer dayNumber;

    /**
     * 当日标题
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
    private Integer sortOrder = 0;
}
