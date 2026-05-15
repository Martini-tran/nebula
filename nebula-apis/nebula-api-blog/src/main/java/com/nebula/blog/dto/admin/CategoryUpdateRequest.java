package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryUpdateRequest {

    @Size(max = 50, message = "分类名称最多 50 个字符")
    private String name;

    @Size(max = 80, message = "slug 最多 80 个字符")
    private String slug;

    @Size(max = 255, message = "描述最多 255 个字符")
    private String description;

    private Long parentId;

    private Integer sortOrder;
}
