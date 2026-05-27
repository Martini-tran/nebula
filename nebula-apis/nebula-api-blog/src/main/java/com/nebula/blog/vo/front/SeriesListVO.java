package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系列列表项VO（前端）
 */
@Data
public class SeriesListVO implements Serializable {

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
     * 已发布文章数量（去重）
     */
    private Integer articleCount;

    /**
     * 排序序号
     */
    private Integer sortOrder;

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
}
