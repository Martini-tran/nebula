package com.nebula.manager.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.PromptPageQuery;
import com.nebula.manager.dto.PromptSaveRequest;
import com.nebula.manager.vo.PromptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI提示词管理控制器（管理员端）
 * 维护 {@code ai_prompt}：分页/详情/创建/更新/删除。提示词按 {@code prompt_code} 被流程/节点引用。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-prompt/prompts")
@RequiredArgsConstructor
public class PromptAdminController {

    private final PromptAdminService promptAdminService;

    /**
     * 分页查询提示词
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("manager:ai-prompt:list")
    public R<PageResult<PromptVO>> page(@ModelAttribute PromptPageQuery query) {
        return R.success(promptAdminService.page(query));
    }

    /**
     * 获取提示词详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("manager:ai-prompt:query")
    public R<PromptVO> detail(@PathVariable Long id) {
        return R.success(promptAdminService.detail(id));
    }

    /**
     * 创建提示词
     */
    @PostMapping
    @SaCheckPermission("manager:ai-prompt:add")
    public R<Long> create(@RequestBody PromptSaveRequest request) {
        return R.success("create success", promptAdminService.create(request));
    }

    /**
     * 更新提示词
     */
    @PutMapping("/{id}")
    @SaCheckPermission("manager:ai-prompt:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody PromptSaveRequest request) {
        promptAdminService.update(id, request);
        return R.success("update success", null);
    }

    /**
     * 删除提示词
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("manager:ai-prompt:delete")
    public R<Void> delete(@PathVariable Long id) {
        promptAdminService.delete(id);
        return R.success("delete success", null);
    }
}
