package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台文章分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostAdminPageQuery extends PageQuery {

    /**
     * 关键词
     */
    private String keyword;

    /**
     * 状态
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
     * 内容类型
     */
    private String postType;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 标签ID
     */
    private Long tagId;
}
