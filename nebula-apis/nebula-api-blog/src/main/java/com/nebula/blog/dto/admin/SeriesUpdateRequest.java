package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * 系列更新请求DTO
 */
@Data
public class SeriesUpdateRequest {

    /**
     * 系列名称
     */
    private String name;

    /**
     * 系列URL标识
     */
    private String slug;

    /**
     * 系列简介
     */
    private String description;

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
     * 是否完结
     */
    private Boolean isFinished;

    /**
     * 排序序号
     */
    private Integer sortOrder;
}
