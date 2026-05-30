package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 系列目录节点VO（前端）
 */
@Data
public class SeriesCatalogNodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点ID
     */
    private Long id;

    /**
     * 父节点ID
     */
    private Long parentId;

    /**
     * 节点标题
     */
    private String title;

    /**
     * 节点类型：0 目录 / 1 文章集合 / 2 链接
     */
    private Integer nodeType;

    /**
     * 链接 URL（node_type=2 时有效）
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

    /**
     * 文章列表（仅 node_type=1 时填充，仅含已发布文章）
     */
    private List<SeriesChapterVO> posts;

    /**
     * 子节点
     */
    private List<SeriesCatalogNodeVO> children;
}
