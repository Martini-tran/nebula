package com.nebula.forge.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractAdminController;
import com.nebula.forge.dto.admin.ForgePluginPermissionBindRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionPageQuery;
import com.nebula.forge.dto.admin.ForgePluginVersionReviewRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionUpdateRequest;
import com.nebula.forge.service.ForgePluginVersionAdminService;
import com.nebula.forge.vo.admin.ForgePluginPermissionAdminVO;
import com.nebula.forge.vo.admin.ForgePluginVersionAdminVO;
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

import java.util.List;

/**
 * 插件版本管理控制器（管理员端）
 * <p>含子资源：版本权限声明</p>
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/plugins/{pluginId}/versions")
@RequiredArgsConstructor
public class ForgePluginVersionAdminController extends AbstractAdminController {

    private final ForgePluginVersionAdminService versionAdminService;

    /**
     * 分页查询版本
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("forge:version:list")
    public R<PageResult<ForgePluginVersionAdminVO>> page(@PathVariable Long pluginId,
                                                         @ModelAttribute ForgePluginVersionPageQuery query) {
        return R.success(versionAdminService.page(pluginId, query));
    }

    /**
     * 版本详情
     */
    @GetMapping("/{versionId}")
    @SaCheckPermission("forge:version:query")
    public R<ForgePluginVersionAdminVO> detail(@PathVariable Long pluginId, @PathVariable Long versionId) {
        return R.success(versionAdminService.detail(pluginId, versionId));
    }

    /**
     * 创建版本
     */
    @PostMapping
    @SaCheckPermission("forge:version:add")
    public R<Long> create(@PathVariable Long pluginId, @RequestBody @Valid ForgePluginVersionCreateRequest req) {
        return R.success(versionAdminService.create(pluginId, req));
    }

    /**
     * 更新版本
     */
    @PutMapping("/{versionId}")
    @SaCheckPermission("forge:version:edit")
    public R<Void> update(@PathVariable Long pluginId, @PathVariable Long versionId,
                          @RequestBody @Valid ForgePluginVersionUpdateRequest req) {
        versionAdminService.update(pluginId, versionId, req);
        return R.success();
    }

    /**
     * 审核版本：1通过 2拒绝
     */
    @PutMapping("/{versionId}/review")
    @SaCheckPermission("forge:version:review")
    public R<Void> review(@PathVariable Long pluginId, @PathVariable Long versionId,
                          @RequestBody @Valid ForgePluginVersionReviewRequest req) {
        versionAdminService.review(pluginId, versionId, req);
        return R.success();
    }

    /**
     * 更新版本状态：0草稿 1发布 2下架 3废弃
     */
    @PutMapping("/{versionId}/status")
    @SaCheckPermission("forge:version:edit")
    public R<Void> updateStatus(@PathVariable Long pluginId, @PathVariable Long versionId,
                                @RequestParam Integer status) {
        versionAdminService.updateStatus(pluginId, versionId, status);
        return R.success();
    }

    /**
     * 删除版本
     */
    @DeleteMapping("/{versionId}")
    @SaCheckPermission("forge:version:delete")
    public R<Void> delete(@PathVariable Long pluginId, @PathVariable Long versionId) {
        versionAdminService.delete(pluginId, versionId);
        return R.success();
    }

    // ============ 版本权限声明（子资源） ============

    /**
     * 查询版本权限声明
     */
    @GetMapping("/{versionId}/permissions")
    @SaCheckPermission("forge:version:query")
    public R<List<ForgePluginPermissionAdminVO>> listPermissions(@PathVariable Long pluginId,
                                                                 @PathVariable Long versionId) {
        return R.success(versionAdminService.listPermissions(pluginId, versionId));
    }

    /**
     * 全量替换版本权限声明
     */
    @PutMapping("/{versionId}/permissions")
    @SaCheckPermission("forge:version:edit")
    public R<Void> bindPermissions(@PathVariable Long pluginId, @PathVariable Long versionId,
                                   @RequestBody @Valid ForgePluginPermissionBindRequest req) {
        versionAdminService.bindPermissions(pluginId, versionId, req);
        return R.success();
    }
}
