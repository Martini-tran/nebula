package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.dto.admin.CategoryCreateRequest;
import com.nebula.blog.dto.admin.CategoryUpdateRequest;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.service.BlogCategoryAdminService;
import com.nebula.blog.vo.admin.CategoryAdminVO;
import com.nebula.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类管理控制器（管理员端）
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryAdminController extends AbstractAdminController {

    private final BlogCategoryAdminService categoryAdminService;

    /**
     * 获取分类树
     */
    @GetMapping
    @SaCheckPermission("blog:category:list")
    public R<List<CategoryAdminVO>> tree() {
        return R.success(categoryAdminService.getAdminTree());
    }

    /**
     * 创建分类
     */
    @PostMapping
    @SaCheckPermission("blog:category:add")
    public R<Long> create(@RequestBody @Valid CategoryCreateRequest req) {
        return R.success(categoryAdminService.create(req));
    }

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:category:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid CategoryUpdateRequest req) {
        categoryAdminService.update(id, req);
        return R.success(null);
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:category:delete")
    public R<Void> delete(@PathVariable Long id) {
        categoryAdminService.delete(id);
        return R.success(null);
    }
}
