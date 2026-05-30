package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 博客文章分类关系表（多对多）
 *
 * @author nebula
 */
@Data
@TableName("blog_post_category")
public class BlogPostCategory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 分类ID
     */
    private Long categoryId;
}