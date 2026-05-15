package com.nebula.blog.service;

import com.nebula.blog.vo.front.CategoryTreeVO;

import java.util.List;

public interface BlogCategoryService {

    List<CategoryTreeVO> getCategoryTree(Long parentId);
}
