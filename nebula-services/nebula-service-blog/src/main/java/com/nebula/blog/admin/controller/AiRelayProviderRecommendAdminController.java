package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.AiRelayProviderRecommendPageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderRecommendRequest;
import com.nebula.blog.service.AiRelayProviderRecommendAdminService;
import com.nebula.blog.vo.admin.AiRelayProviderRecommendAdminVO;
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
 * AI中转服务商推荐 管理控制器
 */
@RestController
@RequestMapping("/admin/ai-relay/recommends")
@RequiredArgsConstructor
public class AiRelayProviderRecommendAdminController extends AbstractAdminController {

    private final AiRelayProviderRecommendAdminService recommendAdminService;

    /**
     * 分页查询推荐
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:ai-relay:recommend:list")
    public R<PageResult<AiRelayProviderRecommendAdminVO>> page(@ModelAttribute AiRelayProviderRecommendPageQuery query) {
        return R.success(recommendAdminService.page(query));
    }

    /**
     * 推荐详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:recommend:query")
    public R<AiRelayProviderRecommendAdminVO> detail(@PathVariable Long id) {
        return R.success(recommendAdminService.detail(id));
    }

    /**
     * 根据服务商ID查询推荐（一对一）
     */
    @GetMapping("/by-provider/{providerId}")
    @SaCheckPermission("blog:ai-relay:recommend:query")
    public R<AiRelayProviderRecommendAdminVO> detailByProvider(@PathVariable Long providerId) {
        return R.success(recommendAdminService.detailByProviderId(providerId));
    }

    /**
     * 创建推荐
     */
    @PostMapping
    @SaCheckPermission("blog:ai-relay:recommend:add")
    public R<Long> create(@RequestBody @Valid AiRelayProviderRecommendRequest req) {
        return R.success(recommendAdminService.create(req));
    }

    /**
     * 更新推荐
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:recommend:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid AiRelayProviderRecommendRequest req) {
        recommendAdminService.update(id, req);
        return R.success();
    }

    /**
     * 切换上下线状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:ai-relay:recommend:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        recommendAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 删除推荐
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:recommend:delete")
    public R<Void> delete(@PathVariable Long id) {
        recommendAdminService.delete(id);
        return R.success();
    }
}
