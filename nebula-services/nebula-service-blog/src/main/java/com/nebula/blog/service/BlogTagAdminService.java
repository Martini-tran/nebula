package com.nebula.blog.service;

import com.nebula.blog.dto.admin.TagCreateRequest;
import com.nebula.blog.dto.admin.TagUpdateRequest;
import com.nebula.blog.vo.admin.TagAdminVO;

import java.util.List;

/**
 * 后台标签管理服务接口
 */
public interface BlogTagAdminService {

    /**
     * 查询标签列表
     */
    List<TagAdminVO> list();

    /**
     * 查询标签详情
     */
    TagAdminVO detail(Long id);

    /**
     * 创建标签
     */
    Long create(TagCreateRequest req);

    /**
     * 更新标签
     */
    void update(Long id, TagUpdateRequest req);

    /**
     * 删除标签
     */
    void delete(Long id);
}
