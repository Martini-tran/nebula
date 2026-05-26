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
 * 博客文章主表
 *
 * @author nebula
 */
@Data
@TableName("blog_post")
public class BlogPost implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 作者用户ID
     */
    private Long authorId;

    /**
     * 内容类型：article(文章)/essay(随笔)
     */
    private String postType;

    /**
     * 文章标题
     */
    private String title;

    /**
     * URL唯一标识
     */
    private String slug;

    /**
     * 摘要
     */
    private String summary;

    /**
     * Markdown正文文件ID（关联blog_file_asset）
     */
    private Long contentFileId;

    /**
     * 封面文件ID（关联blog_file_asset）
     */
    private Long coverFileId;

    /**
     * 状态：draft(草稿)/published(已发布)/archived(已归档)
     */
    private String status;

    /**
     * 可见性：public(公开)/private(私有)
     */
    private String visibility;

    /**
     * 来源：manual(手动)/ai(AI生成)/import(导入)
     */
    private String sourceType;

    /**
     * 是否原创：1-是，0-否
     */
    private Boolean isOriginal;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 点赞次数
     */
    private Integer likeCount;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

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
