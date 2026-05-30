package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行程日VO（管理员端）
 */
@Data
public class TravelTripDayAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tripId;

    private Integer dayNumber;

    private String title;

    private String description;

    private String accommodation;

    private BigDecimal mealCost;

    private BigDecimal transportCost;

    private BigDecimal otherCost;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
