package com.nebula.blog.service;

import com.nebula.blog.dto.admin.CategoryCreateRequest;
import com.nebula.blog.dto.admin.CategoryUpdateRequest;
import com.nebula.blog.vo.admin.CategoryAdminVO;

import java.util.List;

/**
 * 博客分类管理服务接口（管理员端）
 */
public interface BlogCategoryAdminService {

    /**
     * 获取管理员端分类树
     *
     * @return 分类树列表
     */
    List<CategoryAdminVO> getAdminTree();

    /**
     * 创建分类
     *
     * @param req 分类创建请求
     * @return 新建分类ID
     */
    Long create(CategoryCreateRequest req);

    /**
     * 更新分类
     *
     * @param id  分类ID
     * @param req 分类更新请求
     */
    void update(Long id, CategoryUpdateRequest req);

    /**
     * 删除分类
     *
     * @param id 分类ID
     */
    void delete(Long id);
}
