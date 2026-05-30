package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 后台游记分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TravelTripAdminPageQuery extends PageQuery {

    /**
     * 关键词（匹配 title / slug / summary）
     */
    private String keyword;

    /**
     * 状态：draft/published/archived
     */
    private String status;

    /**
     * 可见性：public/private
     */
    private String visibility;

    /**
     * 作者用户ID
     */
    private Long userId;

    /**
     * 出发日期下界
     */
    private LocalDate startDateFrom;

    /**
     * 出发日期上界
     */
    private LocalDate startDateTo;
}
