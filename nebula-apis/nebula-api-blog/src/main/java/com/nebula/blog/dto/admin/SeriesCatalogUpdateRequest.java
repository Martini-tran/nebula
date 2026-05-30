package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * 系列目录节点更新请求
 */
@Data
public class SeriesCatalogUpdateRequest {

    /**
     * 父节点ID（不传则不变；传 0 视为提升为顶层）
     */
    private Long parentId;

    /**
     * 节点标题
     */
    private String title;

    /**
     * 节点类型：0目录 1文章集合 2链接
     */
    private Integer nodeType;

    /**
     * 链接地址
     */
    private String linkUrl;

    /**
     * 链接打开方式：_blank/_self
     */
    private String linkTarget;

    /**
     * 排序序号
     */
    private Integer sortOrder;
}
