package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * 系列目录节点创建请求
 */
@Data
public class SeriesCatalogCreateRequest {

    /**
     * 所属系列ID
     */
    private Long seriesId;

    /**
     * 父节点ID（顶层节点为空）
     */
    private Long parentId;

    /**
     * 节点标题
     */
    private String title;

    /**
     * 节点类型：0目录 1文章集合 2链接，默认 0
     */
    private Integer nodeType = 0;

    /**
     * 链接地址（node_type=2 必填）
     */
    private String linkUrl;

    /**
     * 链接打开方式：_blank/_self，默认 _blank
     */
    private String linkTarget = "_blank";

    /**
     * 排序序号
     */
    private Integer sortOrder = 0;
}
