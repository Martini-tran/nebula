package com.nebula.forge.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractAdminController;
import com.nebula.forge.dto.admin.ForgePluginReviewAuditRequest;
import com.nebula.forge.dto.admin.ForgePluginReviewPageQuery;
import com.nebula.forge.dto.admin.ForgePluginReviewReplyRequest;
import com.nebula.forge.service.ForgePluginReviewAdminService;
import com.nebula.forge.vo.admin.ForgePluginReviewAdminVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件评价管理控制器（管理员端）
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/plugin-reviews")
@RequiredArgsConstructor
public class ForgePluginReviewAdminController extends AbstractAdminController {

    private final ForgePluginReviewAdminService reviewAdminService;

    /**
     * 分页查询评价
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("forge:review:list")
    public R<PageResult<ForgePluginReviewAdminVO>> page(@ModelAttribute ForgePluginReviewPageQuery query) {
        return R.success(reviewAdminService.page(query));
    }

    /**
     * 评价详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("forge:review:query")
    public R<ForgePluginReviewAdminVO> detail(@PathVariable Long id) {
        return R.success(reviewAdminService.detail(id));
    }

    /**
     * 审核评价（设置状态与审核备注）
     */
    @PutMapping("/{id}/audit")
    @SaCheckPermission("forge:review:edit")
    public R<Void> audit(@PathVariable Long id, @RequestBody @Valid ForgePluginReviewAuditRequest req) {
        reviewAdminService.audit(id, req);
        return R.success();
    }

    /**
     * 回复评价
     */
    @PutMapping("/{id}/reply")
    @SaCheckPermission("forge:review:edit")
    public R<Void> reply(@PathVariable Long id, @RequestBody @Valid ForgePluginReviewReplyRequest req) {
        reviewAdminService.reply(id, req);
        return R.success();
    }

    /**
     * 删除评价
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("forge:review:delete")
    public R<Void> delete(@PathVariable Long id) {
        reviewAdminService.delete(id);
        return R.success();
    }
}
