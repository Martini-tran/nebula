package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 行程日VO（前台）
 */
@Data
public class TravelTripDayVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Integer dayNumber;

    private String title;

    private String description;

    private String accommodation;

    private BigDecimal mealCost;

    private BigDecimal transportCost;

    private BigDecimal otherCost;

    private Integer sortOrder;

    /**
     * 当日打卡点列表
     */
    private List<TravelCheckinVO> checkins;
}
