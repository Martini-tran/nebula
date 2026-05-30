package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.AiRelayPackageModelRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageLimitRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackagePageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPackageUpdateRequest;
import com.nebula.blog.service.AiRelayProviderPackageAdminService;
import com.nebula.blog.vo.admin.AiRelayPackageModelAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageLimitAdminVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
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
 * AI中转服务商套餐管理控制器（管理员端）
 * <p>包含子资源：套餐限制、套餐模型</p>
 */
@RestController
@RequestMapping("/admin/ai-relay/packages")
@RequiredArgsConstructor
public class AiRelayProviderPackageAdminController extends AbstractAdminController {

    private final AiRelayProviderPackageAdminService packageAdminService;

    /**
     * 分页查询套餐
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:ai-relay:package:list")
    public R<PageResult<AiRelayProviderPackageAdminVO>> page(@ModelAttribute AiRelayProviderPackagePageQuery query) {
        return R.success(packageAdminService.page(query));
    }

    /**
     * 套餐详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:package:query")
    public R<AiRelayProviderPackageAdminVO> detail(@PathVariable Long id) {
        return R.success(packageAdminService.detail(id));
    }

    /**
     * 创建套餐
     */
    @PostMapping
    @SaCheckPermission("blog:ai-relay:package:add")
    public R<Long> create(@RequestBody @Valid AiRelayProviderPackageCreateRequest req) {
        return R.success(packageAdminService.create(req));
    }

    /**
     * 更新套餐
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid AiRelayProviderPackageUpdateRequest req) {
        packageAdminService.update(id, req);
        return R.success();
    }

    /**
     * 切换套餐状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        packageAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 删除套餐
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:package:delete")
    public R<Void> delete(@PathVariable Long id) {
        packageAdminService.delete(id);
        return R.success();
    }

    // ============ 套餐限制（子资源） ============

    /**
     * 获取套餐限制列表
     */
    @GetMapping("/{packageId}/limits")
    @SaCheckPermission("blog:ai-relay:package:query")
    public R<List<AiRelayProviderPackageLimitAdminVO>> listLimits(@PathVariable Long packageId) {
        return R.success(packageAdminService.listLimits(packageId));
    }

    /**
     * 新增套餐限制
     */
    @PostMapping("/{packageId}/limits")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Long> createLimit(@PathVariable Long packageId,
                               @RequestBody @Valid AiRelayProviderPackageLimitRequest req) {
        return R.success(packageAdminService.createLimit(packageId, req));
    }

    /**
     * 更新套餐限制
     */
    @PutMapping("/{packageId}/limits/{limitId}")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Void> updateLimit(@PathVariable Long packageId,
                               @PathVariable Long limitId,
                               @RequestBody @Valid AiRelayProviderPackageLimitRequest req) {
        packageAdminService.updateLimit(packageId, limitId, req);
        return R.success();
    }

    /**
     * 删除套餐限制
     */
    @DeleteMapping("/{packageId}/limits/{limitId}")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Void> deleteLimit(@PathVariable Long packageId, @PathVariable Long limitId) {
        packageAdminService.deleteLimit(packageId, limitId);
        return R.success();
    }

    // ============ 套餐模型（子资源） ============

    /**
     * 获取套餐支持的模型
     */
    @GetMapping("/{packageId}/models")
    @SaCheckPermission("blog:ai-relay:package:query")
    public R<List<AiRelayPackageModelAdminVO>> listModels(@PathVariable Long packageId) {
        return R.success(packageAdminService.listModels(packageId));
    }

    /**
     * 新增套餐支持的模型
     */
    @PostMapping("/{packageId}/models")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Long> createModel(@PathVariable Long packageId,
                               @RequestBody @Valid AiRelayPackageModelRequest req) {
        return R.success(packageAdminService.createModel(packageId, req));
    }

    /**
     * 更新套餐模型
     */
    @PutMapping("/{packageId}/models/{packageModelId}")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Void> updateModel(@PathVariable Long packageId,
                               @PathVariable Long packageModelId,
                               @RequestBody @Valid AiRelayPackageModelRequest req) {
        packageAdminService.updateModel(packageId, packageModelId, req);
        return R.success();
    }

    /**
     * 移除套餐模型
     */
    @DeleteMapping("/{packageId}/models/{packageModelId}")
    @SaCheckPermission("blog:ai-relay:package:edit")
    public R<Void> deleteModel(@PathVariable Long packageId, @PathVariable Long packageModelId) {
        packageAdminService.deleteModel(packageId, packageModelId);
        return R.success();
    }
}
