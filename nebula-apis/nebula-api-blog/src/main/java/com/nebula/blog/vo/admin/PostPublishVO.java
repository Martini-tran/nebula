package com.nebula.blog.vo.admin;

import lombok.Data;

/**
 * 文章发布结果
 */
@Data
public class PostPublishVO {

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 文章别名
     */
    private String slug;

    /**
     * 本次是新建（true）还是更新了已有文章（false）
     */
    private Boolean created;

    /**
     * 挂到的系列ID，未指定系列时为空
     */
    private Long seriesId;

    /**
     * 挂到的目录节点ID，未指定系列时为空
     */
    private Long catalogId;
}
