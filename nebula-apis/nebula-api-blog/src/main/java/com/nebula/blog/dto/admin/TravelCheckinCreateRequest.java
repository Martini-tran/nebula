package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡点创建请求
 */
@Data
public class TravelCheckinCreateRequest {

    /**
     * 所属行程日ID
     */
    private Long tripDayId;

    /**
     * 关联的目的地ID（与 customName 至少二选一）
     */
    private Long destinationId;

    /**
     * 自定义地点名称
     */
    private String customName;

    /**
     * 自定义经度
     */
    private BigDecimal customLongitude;

    /**
     * 自定义纬度
     */
    private BigDecimal customLatitude;

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
    private Integer sortOrder = 0;
}
