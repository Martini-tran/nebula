package com.nebula.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游记与博客文章关联
 *
 * @author nebula
 */
@Data
@TableName("travel_trip_blog_post")
public class TravelTripBlogPost implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 游记ID
     */
    private Long tripId;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 关联类型：0主要文章 1相关推荐
     */
    private Integer postType;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}