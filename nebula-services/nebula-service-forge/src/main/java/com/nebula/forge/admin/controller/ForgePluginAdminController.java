package com.nebula.forge.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractAdminController;
import com.nebula.forge.dto.admin.ForgePluginCategoryBindRequest;
import com.nebula.forge.dto.admin.ForgePluginCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginPageQuery;
import com.nebula.forge.dto.admin.ForgePluginUpdateRequest;
import com.nebula.forge.service.ForgePluginAdminService;
import com.nebula.forge.vo.admin.ForgePluginAdminVO;
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
 * 插件管理控制器（管理员端）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/plugins")
@RequiredArgsConstructor
public class ForgePluginAdminController extends AbstractAdminController {

    private final ForgePluginAdminService pluginAdminService;

    /**
     * 分页查询插件
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("forge:plugin:list")
    public R<PageResult<ForgePluginAdminVO>> page(@ModelAttribute ForgePluginPageQuery query) {
        return R.success(pluginAdminService.page(query));
    }

    /**
     * 插件详情（含关联分类）
     */
    @GetMapping("/{id}")
    @SaCheckPermission("forge:plugin:query")
    public R<ForgePluginAdminVO> detail(@PathVariable Long id) {
        return R.success(pluginAdminService.detail(id));
    }

    /**
     * 创建插件
     */
    @PostMapping
    @SaCheckPermission("forge:plugin:add")
    public R<Long> create(@RequestBody @Valid ForgePluginCreateRequest req) {
        return R.success(pluginAdminService.create(req));
    }

    /**
     * 更新插件
     */
    @PutMapping("/{id}")
    @SaCheckPermission("forge:plugin:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid ForgePluginUpdateRequest req) {
        pluginAdminService.update(id, req);
        return R.success();
    }

    /**
     * 更新插件状态：0草稿 1上架 2下架 3封禁
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("forge:plugin:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        pluginAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 切换推荐标记：1是 0否
     */
    @PutMapping("/{id}/featured")
    @SaCheckPermission("forge:plugin:edit")
    public R<Void> updateFeatured(@PathVariable Long id, @RequestParam Integer isFeatured) {
        pluginAdminService.updateFeatured(id, isFeatured);
        return R.success();
    }

    /**
     * 全量重绑插件关联分类
     */
    @PutMapping("/{id}/categories")
    @SaCheckPermission("forge:plugin:edit")
    public R<Void> bindCategories(@PathVariable Long id, @RequestBody @Valid ForgePluginCategoryBindRequest req) {
        pluginAdminService.bindCategories(id, req);
        return R.success();
    }

    /**
     * 删除插件
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("forge:plugin:delete")
    public R<Void> delete(@PathVariable Long id) {
        pluginAdminService.delete(id);
        return R.success();
    }
}
