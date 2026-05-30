package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 游记VO（管理员端）
 */
@Data
public class TravelTripAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String title;

    private String slug;

    private String summary;

    private Long coverFileId;

    private String coverUrl;

    /**
     * draft/published/archived
     */
    private String status;

    /**
     * public/private
     */
    private String visibility;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer daysCount;

    private Integer persons;

    private BigDecimal costTotal;

    private String costCurrency;

    private Integer viewCount;

    private Integer likeCount;

    private LocalDateTime publishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
