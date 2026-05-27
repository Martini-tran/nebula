package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡点更新请求
 */
@Data
public class TravelCheckinUpdateRequest {

    /**
     * 关联的目的地ID
     */
    private Long destinationId;

    /**
     * 是否清空 destinationId
     */
    private Boolean clearDestinationId;

    /**
     * 自定义地点名称
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
     * 照片ID数组（JSON 字符串）
     */
    private String photos;

    /**
     * 当日内的顺序
     */
    private Integer sortOrder;
}
