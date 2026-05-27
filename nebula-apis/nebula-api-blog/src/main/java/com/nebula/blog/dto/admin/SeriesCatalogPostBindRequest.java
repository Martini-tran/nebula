package com.nebula.blog.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 目录文章绑定请求（全量替换该目录下的文章关联）
 */
@Data
public class SeriesCatalogPostBindRequest {

    /**
     * 文章ID列表
     */
    private List<Long> postIds;

    /**
     * 主目录文章ID（可选，标记 is_primary=1）
     */
    private Long primaryPostId;
}
