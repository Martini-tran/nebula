package com.nebula.space.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 后台书签创建请求
 */
@Data
public class BookmarkCreateRequest {

    /**
     * 所属目录ID，0表示未分类
     */
    private Long folderId;

    /**
     * 书签标题
     */
    @NotBlank(message = "书签标题不能为空")
    @Size(max = 500, message = "书签标题长度不能超过500")
    private String title;

    /**
     * 原始URL
     */
    @NotBlank(message = "URL不能为空")
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
     * 站点图标文件ID，关联sys_file
     */
    private Long faviconFileId;

    /**
     * 来源：manual/chrome/import
     */
    private String source;

    /**
     * 外部来源标识
     */
    @Size(max = 255)
    private String sourceKey;

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
     * 标签ID列表，绑定关系
     */
    private List<Long> tagIds;
}
