package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 游记列表项VO（前台）
 */
@Data
public class TravelTripListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String slug;

    private String title;

    private String summary;

    private String coverUrl;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer daysCount;

    private Integer persons;

    private BigDecimal costTotal;

    private String costCurrency;

    private Integer viewCount;

    private Integer likeCount;

    private LocalDateTime publishedAt;
}
