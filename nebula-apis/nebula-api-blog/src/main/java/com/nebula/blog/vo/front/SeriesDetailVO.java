package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系列详情VO（前端）
 *
 * <p>同时返回扁平的 chapters（按目录顺序汇总的已发布文章）和层级化的 catalog（目录树），
 * 便于前端按需渲染：简单页面遍历 chapters，复杂页面渲染 catalog 树。
 */
@Data
public class SeriesDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 系列ID
     */
    private Long id;

    /**
     * 系列URL标识
     */
    private String slug;

    /**
     * 系列名称
     */
    private String name;

    /**
     * 系列简介
     */
    private String description;

    /**
     * 封面URL
     */
    private String coverUrl;

    /**
     * 是否完结
     */
    private Boolean isFinished;

    /**
     * 已发布文章数量（chapters 长度）
     */
    private Integer articleCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 关联标签（聚合自系列内所有已发布文章）
     */
    private List<TagSummaryVO> tags;

    /**
     * 扁平章节列表，按目录 sort_order → 文章 sort_order 排好
     */
    private List<SeriesChapterVO> chapters;

    /**
     * 完整目录树
     */
    private List<SeriesCatalogNodeVO> catalog;
}
