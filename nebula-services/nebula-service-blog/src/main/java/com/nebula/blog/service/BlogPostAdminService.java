package com.nebula.blog.service;

import com.nebula.blog.dto.admin.PostAdminPageQuery;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.PostStatusUpdateRequest;
import com.nebula.blog.dto.admin.PostUpdateRequest;
import com.nebula.blog.vo.admin.PostAdminVO;
import com.nebula.common.core.domain.PageResult;

/**
 * 后台文章管理服务接口
 */
public interface BlogPostAdminService {

    /**
     * 分页查询文章
     */
    PageResult<PostAdminVO> page(PostAdminPageQuery query);

    /**
     * 查看文章详情
     */
    PostAdminVO detail(Long id);

    /**
     * 新建文章
     */
    Long create(PostCreateRequest req);

    /**
     * 更新文章
     */
    void update(Long id, PostUpdateRequest req);

    /**
     * 删除文章
     */
    void delete(Long id);

    /**
     * 更新文章状态
     */
    void updateStatus(Long id, PostStatusUpdateRequest req);
}
