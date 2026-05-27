package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡点VO（管理员端）
 */
@Data
public class TravelCheckinAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tripDayId;

    private Long destinationId;

    /**
     * 关联目的地的名称（destinationId 为空时为 null）
     */
    private String destinationName;

    private String customName;

    /**
     * 自定义经度
     */
    private BigDecimal customLongitude;

    /**
     * 自定义纬度
     */
    private BigDecimal customLatitude;

    private LocalDateTime arrivalTime;

    private LocalDateTime departureTime;

    private String notes;

    private BigDecimal rating;

    /**
     * 照片ID数组（JSON 字符串）
     */
    private String photos;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
