package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.AiRelayModelPageQuery;
import com.nebula.blog.dto.admin.AiRelayModelRequest;
import com.nebula.blog.service.AiRelayModelAdminService;
import com.nebula.blog.vo.admin.AiRelayModelAdminVO;
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

/**
 * AI模型管理控制器（管理员端）
 */
@RestController
@RequestMapping("/admin/ai-relay/models")
@RequiredArgsConstructor
public class AiRelayModelAdminController extends AbstractAdminController {

    private final AiRelayModelAdminService modelAdminService;

    /**
     * 分页查询模型
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:ai-relay:model:list")
    public R<PageResult<AiRelayModelAdminVO>> page(@ModelAttribute AiRelayModelPageQuery query) {
        return R.success(modelAdminService.page(query));
    }

    /**
     * 模型详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:model:query")
    public R<AiRelayModelAdminVO> detail(@PathVariable Long id) {
        return R.success(modelAdminService.detail(id));
    }

    /**
     * 创建模型
     */
    @PostMapping
    @SaCheckPermission("blog:ai-relay:model:add")
    public R<Long> create(@RequestBody @Valid AiRelayModelRequest req) {
        return R.success(modelAdminService.create(req));
    }

    /**
     * 更新模型
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:model:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid AiRelayModelRequest req) {
        modelAdminService.update(id, req);
        return R.success();
    }

    /**
     * 切换模型状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:ai-relay:model:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        modelAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:model:delete")
    public R<Void> delete(@PathVariable Long id) {
        modelAdminService.delete(id);
        return R.success();
    }
}
