package com.nebula.blog.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 游记 ↔ 博客文章关联绑定请求（全量替换）
 */
@Data
public class TravelTripPostBindRequest {

    /**
     * 文章ID列表
     */
    private List<Long> postIds;

    /**
     * 主要文章ID（标记 post_type=0；其余为 1 相关推荐）
     */
    private Long primaryPostId;
}
