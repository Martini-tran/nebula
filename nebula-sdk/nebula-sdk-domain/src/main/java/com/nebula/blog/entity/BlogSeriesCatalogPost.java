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
 * 目录文章关联表
 *
 * @author nebula
 */
@Data
@TableName("blog_series_catalog_post")
public class BlogSeriesCatalogPost implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联ID
     */
    @TableId(value = "id", type = IdType.AUTO)
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
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 是否为主目录：1-是，0-否
     */
    private Boolean isPrimary;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
