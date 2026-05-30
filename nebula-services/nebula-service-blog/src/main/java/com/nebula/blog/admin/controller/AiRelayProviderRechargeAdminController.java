package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.AiRelayProviderRechargePageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderRechargeRequest;
import com.nebula.blog.service.AiRelayProviderRechargeAdminService;
import com.nebula.blog.vo.admin.AiRelayProviderRechargeAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderRechargeStatsVO;
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
 * AI中转服务商充值记录 管理控制器
 */
@RestController
@RequestMapping("/admin/ai-relay/recharges")
@RequiredArgsConstructor
public class AiRelayProviderRechargeAdminController extends AbstractAdminController {

    private final AiRelayProviderRechargeAdminService rechargeAdminService;

    /**
     * 分页查询充值记录
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:ai-relay:recharge:list")
    public R<PageResult<AiRelayProviderRechargeAdminVO>> page(@ModelAttribute AiRelayProviderRechargePageQuery query) {
        return R.success(rechargeAdminService.page(query));
    }

    /**
     * 充值记录详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:recharge:query")
    public R<AiRelayProviderRechargeAdminVO> detail(@PathVariable Long id) {
        return R.success(rechargeAdminService.detail(id));
    }

    /**
     * 创建充值记录
     */
    @PostMapping
    @SaCheckPermission("blog:ai-relay:recharge:add")
    public R<Long> create(@RequestBody @Valid AiRelayProviderRechargeRequest req) {
        return R.success(rechargeAdminService.create(req));
    }

    /**
     * 更新充值记录
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:recharge:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid AiRelayProviderRechargeRequest req) {
        rechargeAdminService.update(id, req);
        return R.success();
    }

    /**
     * 删除充值记录
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:recharge:delete")
    public R<Void> delete(@PathVariable Long id) {
        rechargeAdminService.delete(id);
        return R.success();
    }

    /**
     * 充值汇总统计（按服务商）
     */
    @GetMapping("/stats/by-provider")
    @SaCheckPermission("blog:ai-relay:recharge:list")
    public R<List<AiRelayProviderRechargeStatsVO>> statsByProvider(@RequestParam(required = false) Long providerId) {
        return R.success(rechargeAdminService.statsByProvider(providerId));
    }
}
