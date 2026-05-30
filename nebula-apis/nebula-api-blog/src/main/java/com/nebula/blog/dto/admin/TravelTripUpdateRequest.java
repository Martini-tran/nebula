package com.nebula.blog.dto.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 游记更新请求
 */
@Data
public class TravelTripUpdateRequest {

    /**
     * 标题
     */
    private String title;

    /**
     * URL标识
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
     * 是否清空封面（前端显式信号）
     */
    private Boolean clearCoverFileId;

    /**
     * 状态：draft/published/archived
     */
    private String status;

    /**
     * 可见性：public/private
     */
    private String visibility;

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
     * 货币
     */
    private String costCurrency;
}
