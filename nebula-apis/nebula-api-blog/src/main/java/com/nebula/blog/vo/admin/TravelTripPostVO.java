package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游记 ↔ 博客文章 关联VO
 */
@Data
public class TravelTripPostVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tripId;

    private Long postId;

    /**
     * 关联类型：0主要文章 1相关推荐
     */
    private Integer postType;

    private String postTitle;

    private String postSlug;

    private String postStatus;

    private LocalDateTime createTime;
}
