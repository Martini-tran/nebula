package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * 系列创建请求DTO
 */
@Data
public class SeriesCreateRequest {

    /**
     * 系列名称
     */
    private String name;

    /**
     * 系列URL标识（唯一）
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
     * 状态：draft/published/archived，默认 draft
     */
    private String status = "draft";

    /**
     * 可见性：public/private，默认 public
     */
    private String visibility = "public";

    /**
     * 是否完结
     */
    private Boolean isFinished = false;

    /**
     * 排序序号
     */
    private Integer sortOrder = 0;
}
