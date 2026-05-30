package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.AiRelayPackageTypeRequest;
import com.nebula.blog.service.AiRelayPackageTypeAdminService;
import com.nebula.blog.vo.admin.AiRelayPackageTypeAdminVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI中转套餐类型 管理控制器（字典）
 */
@RestController
@RequestMapping("/admin/ai-relay/package-types")
@RequiredArgsConstructor
public class AiRelayPackageTypeAdminController extends AbstractAdminController {

    private final AiRelayPackageTypeAdminService packageTypeAdminService;

    /**
     * 套餐类型列表
     */
    @GetMapping
    @SaCheckPermission("blog:ai-relay:package-type:list")
    public R<List<AiRelayPackageTypeAdminVO>> list(@RequestParam(required = false) Integer status) {
        return R.success(packageTypeAdminService.list(status));
    }

    /**
     * 套餐类型详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:package-type:query")
    public R<AiRelayPackageTypeAdminVO> detail(@PathVariable Long id) {
        return R.success(packageTypeAdminService.detail(id));
    }

    /**
     * 创建套餐类型
     */
    @PostMapping
    @SaCheckPermission("blog:ai-relay:package-type:add")
    public R<Long> create(@RequestBody @Valid AiRelayPackageTypeRequest req) {
        return R.success(packageTypeAdminService.create(req));
    }

    /**
     * 更新套餐类型
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:package-type:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid AiRelayPackageTypeRequest req) {
        packageTypeAdminService.update(id, req);
        return R.success();
    }

    /**
     * 切换状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:ai-relay:package-type:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        packageTypeAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 删除套餐类型
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:package-type:delete")
    public R<Void> delete(@PathVariable Long id) {
        packageTypeAdminService.delete(id);
        return R.success();
    }
}
