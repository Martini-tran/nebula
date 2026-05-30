package com.nebula.space.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 后台书签更新请求
 */
@Data
public class BookmarkUpdateRequest {

    /**
     * 所属目录ID
     */
    private Long folderId;

    /**
     * 书签标题
     */
    @Size(max = 500)
    private String title;

    /**
     * 原始URL
     */
    private String url;

    /**
     * 描述
     */
    @Size(max = 1000)
    private String description;

    /**
     * 站点图标URL
     */
    @Size(max = 1000)
    private String faviconUrl;

    /**
     * 站点图标文件ID
     */
    private Long faviconFileId;

    /**
     * 目录内排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    @Size(max = 500)
    private String remark;

    /**
     * 标签ID列表，传入则替换原有标签关系；为 null 表示不修改
     */
    private List<Long> tagIds;
}
