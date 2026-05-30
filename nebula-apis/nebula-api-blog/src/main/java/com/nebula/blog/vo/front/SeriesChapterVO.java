package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系列章节项VO（前端，对应一篇已发布文章）
 */
@Data
public class SeriesChapterVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章别名
     */
    private String slug;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 状态：published / draft / archived
     */
    private String status;

    /**
     * 所属目录节点ID
     */
    private Long catalogId;

    /**
     * 所属目录节点标题
     */
    private String catalogTitle;

    /**
     * 是否为该目录下的主目录文章
     */
    private Boolean isPrimary;

    /**
     * 文章在系列内的展示顺序（catalog 顺序 → post 顺序）
     */
    private Integer order;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;
}
