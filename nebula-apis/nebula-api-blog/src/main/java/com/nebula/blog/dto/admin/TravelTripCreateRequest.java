package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 游记创建请求
 */
@Data
public class TravelTripCreateRequest {

    /**
     * 标题
     */
    private String title;

    /**
     * URL标识（唯一）
     */
    private String slug;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 状态：draft/published/archived，默认 draft
     */
    private String status = "draft";

    /**
     * 可见性：public/private，默认 public
     */
    private String visibility = "public";

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    /**
     * 人数
     */
    private Integer persons;

    /**
     * 总花费
     */
    private BigDecimal costTotal;

    /**
     * 货币（CNY/USD等）
     */
    private String costCurrency = "CNY";
}
