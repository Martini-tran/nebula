package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台系列分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SeriesAdminPageQuery extends PageQuery {

    /**
     * 关键词（匹配 name / slug / description）
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
     * 是否完结
     */
    private Boolean isFinished;

    /**
     * 创建者ID
     */
    private Long createBy;
}
