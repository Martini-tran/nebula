package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项VO（前端）
 */
@Data
public class PostListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 文章别名（URL友好）
     */
    private String slug;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章摘要
     */
    private String summary;

    /**
     * 封面图片URL
     */
    private String coverUrl;

    /**
     * 分类列表
     */
    private List<CategorySummaryVO> categories;

    /**
     * 标签列表
     */
    private List<TagSummaryVO> tags;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 点赞次数
     */
    private Integer likeCount;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;
}
