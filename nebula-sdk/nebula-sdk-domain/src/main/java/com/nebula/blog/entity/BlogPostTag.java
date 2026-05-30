package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 博客文章标签关系表（多对多）
 *
 * @author nebula
 */
@Data
@TableName("blog_post_tag")
public class BlogPostTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 标签ID
     */
    private Long tagId;
}