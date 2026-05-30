package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系列目录节点VO（管理员端）
 */
@Data
public class SeriesCatalogAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点ID
     */
    private Long id;

    /**
     * 系列ID
     */
    private Long seriesId;

    /**
     * 父节点ID
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
     * 链接地址（node_type=2 时有效）
     */
    private String linkUrl;

    /**
     * 链接打开方式：_blank/_self
     */
    private String linkTarget;

    /**
     * 树路径
     */
    private String path;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 子节点数量
     */
    private Integer childrenCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 子节点列表
     */
    private List<SeriesCatalogAdminVO> children;
}
