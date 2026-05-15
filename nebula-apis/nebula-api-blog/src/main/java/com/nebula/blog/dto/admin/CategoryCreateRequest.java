package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * 分类创建请求DTO
 */
@Data
public class CategoryCreateRequest {

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类别名（URL友好）
     */
    private String slug;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 排序序号，默认0
     */
    private Integer sortOrder = 0;
}
