package com.nebula.blog.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章发布请求（供脚本等外部调用方使用）
 * 以 slug 为幂等键：slug 不存在则新建，已存在则更新，可放心重跑。可选同时把文章挂到系列目录。
 */
@Data
public class PostPublishRequest {

    /**
     * 文章标题（新建时必填）
     */
    private String title;

    /**
     * 文章别名，幂等键，必填
     */
    private String slug;

    /**
     * 内容类型：article(文章)/essay(随笔)
     */
    private String postType;

    /**
     * 摘要
     */
    private String summary;

    /**
     * Markdown正文内容（新建时必填）
     */
    private String content;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 草稿/已发布/已归档，缺省为已发布
     */
    private String status;

    /**
     * 公开/私有
     */
    private String visibility;

    /**
     * 手动/AI/导入
     */
    private String sourceType;

    /**
     * 是否原创
     */
    private Boolean isOriginal;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 关联分类；更新时不传表示不改
     */
    private List<Long> categoryIds;

    /**
     * 关联标签；更新时不传表示不改
     */
    private List<Long> tagIds;

    /**
     * 要挂到的系列，不传则不处理系列关联
     */
    private SeriesRef series;

    /**
     * 系列定位：文章不直接属于系列，而是挂在系列下的某个「文章集合」目录节点上
     */
    @Data
    public static class SeriesRef {

        /**
         * 系列ID，与 slug 二选一，都传以 ID 为准
         */
        private Long id;

        /**
         * 系列别名
         */
        private String slug;

        /**
         * 目录节点ID，须属于该系列且为「文章集合」类型
         */
        private Long catalogId;

        /**
         * 目录节点标题：按标题找「文章集合」节点，找不到则在系列根下新建。
         * catalogId 与本字段都不传时，用系列下第一个「文章集合」节点，没有则新建「正文」
         */
        private String catalog;

        /**
         * 在目录节点内的排序，不传则排到末尾（已挂载过则保持原位）
         */
        private Integer sortOrder;
    }
}
