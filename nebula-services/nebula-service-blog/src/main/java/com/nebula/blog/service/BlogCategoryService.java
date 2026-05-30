package com.nebula.blog.service;

import com.nebula.blog.vo.front.CategoryTreeVO;

import java.util.List;

/**
 * 博客分类服务接口（前端）
 */
public interface BlogCategoryService {

    /**
     * 获取分类树
     *
     * @param parentId 父分类ID，为null时从根节点开始
     * @return 分类树列表
     */
    List<CategoryTreeVO> getCategoryTree(Long parentId);
}
