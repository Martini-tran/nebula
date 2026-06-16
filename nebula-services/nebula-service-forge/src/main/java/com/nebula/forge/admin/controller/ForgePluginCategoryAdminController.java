package com.nebula.forge.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractAdminController;
import com.nebula.forge.dto.admin.ForgePluginCategoryCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginCategoryPageQuery;
import com.nebula.forge.dto.admin.ForgePluginCategoryUpdateRequest;
import com.nebula.forge.service.ForgePluginCategoryAdminService;
import com.nebula.forge.vo.admin.ForgePluginCategoryAdminVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件分类管理控制器（管理员端）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/plugin-categories")
@RequiredArgsConstructor
public class ForgePluginCategoryAdminController extends AbstractAdminController {

    private final ForgePluginCategoryAdminService categoryAdminService;

    /**
     * 分页查询插件分类
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("forge:category:list")
    public R<PageResult<ForgePluginCategoryAdminVO>> page(@ModelAttribute ForgePluginCategoryPageQuery query) {
        return R.success(categoryAdminService.page(query));
    }

    /**
     * 插件分类详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("forge:category:query")
    public R<ForgePluginCategoryAdminVO> detail(@PathVariable Long id) {
        return R.success(categoryAdminService.detail(id));
    }

    /**
     * 创建插件分类
     */
    @PostMapping
    @SaCheckPermission("forge:category:add")
    public R<Long> create(@RequestBody @Valid ForgePluginCategoryCreateRequest req) {
        return R.success(categoryAdminService.create(req));
    }

    /**
     * 更新插件分类
     */
    @PutMapping("/{id}")
    @SaCheckPermission("forge:category:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid ForgePluginCategoryUpdateRequest req) {
        categoryAdminService.update(id, req);
        return R.success();
    }

    /**
     * 切换分类启用/停用状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("forge:category:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        categoryAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 删除插件分类
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("forge:category:delete")
    public R<Void> delete(@PathVariable Long id) {
        categoryAdminService.delete(id);
        return R.success();
    }
}
