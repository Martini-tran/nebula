package com.nebula.blog.dto.admin;

import lombok.Data;

@Data
public class CategoryUpdateRequest {

    private String name;

    private String slug;


    private String description;

    private Long parentId;

    private Integer sortOrder;
}
