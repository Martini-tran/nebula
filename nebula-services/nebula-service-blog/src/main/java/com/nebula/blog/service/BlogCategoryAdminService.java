package com.nebula.blog.service;

import com.nebula.blog.dto.admin.CategoryCreateRequest;
import com.nebula.blog.dto.admin.CategoryUpdateRequest;
import com.nebula.blog.vo.admin.CategoryAdminVO;

import java.util.List;

public interface BlogCategoryAdminService {

    List<CategoryAdminVO> getAdminTree();

    Long create(CategoryCreateRequest req);

    void update(Long id, CategoryUpdateRequest req);

    void delete(Long id);
}
