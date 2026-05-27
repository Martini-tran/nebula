package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行程打卡点VO（前台）
 */
@Data
public class TravelCheckinVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String customName;

    private Long destinationId;

    private String destinationName;

    private String customLocation;

    private LocalDateTime arrivalTime;

    private LocalDateTime departureTime;

    private String notes;

    private BigDecimal rating;

    /**
     * 照片URL列表（已解析为可访问URL）
     */
    private List<String> photoUrls;

    private Integer sortOrder;
}
