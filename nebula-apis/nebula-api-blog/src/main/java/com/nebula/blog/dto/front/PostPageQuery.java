package com.nebula.blog.dto.front;

import lombok.Data;

/**
 * 文章分页查询DTO
 */
@Data
public class PostPageQuery {

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 关键词
     */
    private String keyword;

    /**
     * 内容类型：article(文章)/essay(随笔)
     */
    private String postType = "article";

    /**
     * 游标（用于分页）
     */
    private String cursor;

    /**
     * 每页数量，默认10
     */
    private Integer limit = 10;
}
