package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系列目录节点表
 *
 * @author nebula
 */
@Data
@TableName("blog_series_catalog")
public class BlogSeriesCatalog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点ID
     */
    @TableId(value = "id", type = IdType.AUTO)
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
     * 链接地址（node_type=2时有效）
     */
    private String linkUrl;

    /**
     * 链接打开方式：_blank/_self
     */
    private String linkTarget;

    /**
     * 树路径，如 /1/5/12/
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
