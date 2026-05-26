package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
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
     * 内容类型：article / essay（可过滤）
     */
    private String postType;

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
     * 发布时间（Unix 秒，可排序）。
     * 使用 epoch 秒而非 LocalDateTime，避免 Gson 反射序列化 java.time 类时触发
     * Java 9+ 模块系统的访问限制（JsonIOException: Failed making field accessible）。
     */
    private Long publishedAt;

    /**
     * 创建时间（Unix 秒，可排序）
     */
    private Long createTime;

    /**
     * 最后更新时间（Unix 秒，可排序）
     */
    private Long updateTime;
}
