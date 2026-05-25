package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章 Meilisearch 索引文档 DTO
 * <p>
 * 该对象直接被序列化后提交给 Meilisearch，字段即索引文档的属性。
 * 主键字段为 id，与 Meilisearch 索引配置的 primary-key 保持一致。
 */
@Data
public class PostSearchDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章 ID（Meilisearch 主键）
     */
    private Long id;

    /**
     * 文章标题（可搜索）
     */
    private String title;

    /**
     * 文章摘要（可搜索）
     */
    private String summary;

    /**
     * URL 唯一标识
     */
    private String slug;

    /**
     * 状态：draft / published / archived（可过滤）
     */
    private String status;

    /**
     * 可见性：public / private（可过滤）
     */
    private String visibility;

    /**
     * 作者 ID（可过滤）
     */
    private Long authorId;

    /**
     * 关联分类 ID 列表（可过滤）
     */
    private List<Long> categoryIds;

    /**
     * 关联分类名称列表（可搜索）
     */
    private List<String> categoryNames;

    /**
     * 关联标签 ID 列表（可过滤）
     */
    private List<Long> tagIds;

    /**
     * 关联标签名称列表（可搜索）
     */
    private List<String> tagNames;

    /**
     * 发布时间（可排序）
     */
    private LocalDateTime publishedAt;

    /**
     * 创建时间（可排序）
     */
    private LocalDateTime createTime;

    /**
     * 最后更新时间（可排序）
     */
    private LocalDateTime updateTime;
}
