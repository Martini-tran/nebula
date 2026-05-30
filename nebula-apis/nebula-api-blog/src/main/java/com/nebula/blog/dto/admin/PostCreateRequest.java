package com.nebula.blog.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台文章创建请求
 */
@Data
public class PostCreateRequest {

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章别名
     */
    private String slug;

    /**
     * 内容类型：article(文章)/essay(随笔)
     */
    private String postType = "article";

    /**
     * 摘要
     */
    private String summary;

    /**
     * Markdown正文内容
     */
    private String content;

    /**
     * 正文文件ID
     */
    private Long contentFileId;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 草稿/已发布/已归档
     */
    private String status = "draft";

    /**
     * 公开/私有
     */
    private String visibility = "public";

    /**
     * 手动/AI/导入
     */
    private String sourceType = "manual";

    /**
     * 是否原创
     */
    private Boolean isOriginal = true;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 关联分类
     */
    private List<Long> categoryIds;

    /**
     * 关联标签
     */
    private List<Long> tagIds;
}
