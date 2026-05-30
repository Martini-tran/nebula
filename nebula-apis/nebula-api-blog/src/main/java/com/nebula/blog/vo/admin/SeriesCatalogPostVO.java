package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 目录文章关联VO
 */
@Data
public class SeriesCatalogPostVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联ID
     */
    private Long id;

    /**
     * 目录节点ID
     */
    private Long catalogId;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 文章标题
     */
    private String postTitle;

    /**
     * 文章别名
     */
    private String postSlug;

    /**
     * 文章状态
     */
    private String postStatus;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 是否为主目录
     */
    private Boolean isPrimary;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
