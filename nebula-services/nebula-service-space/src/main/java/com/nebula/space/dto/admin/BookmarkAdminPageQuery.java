package com.nebula.space.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台书签分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookmarkAdminPageQuery extends PageQuery {

    /**
     * 关键词，匹配标题/URL/描述
     */
    private String keyword;

    /**
     * 所属用户ID（仅超级管理员可跨用户查询）
     */
    private Long userId;

    /**
     * 目录ID
     */
    private Long folderId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 状态：0正常 1归档 2失效
     */
    private Integer status;

    /**
     * 来源：manual/chrome/import
     */
    private String source;

    /**
     * 域名
     */
    private String domain;
}
