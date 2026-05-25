package com.nebula.blog.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台文章更新请求
 */
@Data
public class PostUpdateRequest {

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章别名
     */
    private String slug;

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
     * 是否清除封面图片。
     * <p>
     * 由于 JSON 无法区分"字段缺失"与"显式传 null"，
     * 前端移除封面时需将此字段设为 true，后端收到后将 coverFileId 置为 null。
     * 与 coverFileId 互斥：若 clearCoverFileId=true，则忽略 coverFileId。
     */
    private Boolean clearCoverFileId;

    /**
     * 文章状态
     */
    private String status;

    /**
     * 可见性
     */
    private String visibility;

    /**
     * 来源类型
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
     * 关联分类
     */
    private List<Long> categoryIds;

    /**
     * 关联标签
     */
    private List<Long> tagIds;

    /**
     * 变更说明（写入快照的 changeNote 字段，可选）
     */
    private String changeNote;
}
