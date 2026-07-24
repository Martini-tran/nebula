package com.nebula.manager.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.ModelProfilePageQuery;
import com.nebula.manager.dto.ModelProfileSaveRequest;
import com.nebula.manager.vo.ModelProfileVO;
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
 * AI模型档案管理控制器（管理员端）
 * 维护 {@code ai_model_profile}：分页/详情/创建/更新/删除/启停。apiKey 入参为明文（服务端加密落库），
 * 出参一律掩码、不回传明文。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-model-profile/profiles")
@RequiredArgsConstructor
public class ModelProfileAdminController {

    private final ModelProfileAdminService modelProfileAdminService;

    /**
     * 分页查询模型档案
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("manager:ai-model-profile:list")
    public R<PageResult<ModelProfileVO>> page(@ModelAttribute ModelProfilePageQuery query) {
        return R.success(modelProfileAdminService.page(query));
    }

    /**
     * 获取模型档案详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("manager:ai-model-profile:query")
    public R<ModelProfileVO> detail(@PathVariable Long id) {
        return R.success(modelProfileAdminService.detail(id));
    }

    /**
     * 创建模型档案
     */
    @PostMapping
    @SaCheckPermission("manager:ai-model-profile:add")
    public R<Long> create(@RequestBody ModelProfileSaveRequest request) {
        return R.success("create success", modelProfileAdminService.create(request));
    }

    /**
     * 更新模型档案
     */
    @PutMapping("/{id}")
    @SaCheckPermission("manager:ai-model-profile:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody ModelProfileSaveRequest request) {
        modelProfileAdminService.update(id, request);
        return R.success("update success", null);
    }

    /**
     * 删除模型档案
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("manager:ai-model-profile:delete")
    public R<Void> delete(@PathVariable Long id) {
        modelProfileAdminService.delete(id);
        return R.success("delete success", null);
    }

    /**
     * 更新启用/停用状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("manager:ai-model-profile:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        modelProfileAdminService.updateStatus(id, status);
        return R.success("status updated", null);
    }
}
