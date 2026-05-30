package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.AiRelayProviderAdvantageRequest;
import com.nebula.blog.dto.admin.AiRelayProviderCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPaymentMethodBindRequest;
import com.nebula.blog.dto.admin.AiRelayProviderUpdateRequest;
import com.nebula.blog.service.AiRelayProviderAdminService;
import com.nebula.blog.vo.admin.AiRelayProviderAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderAdvantageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPaymentMethodAdminVO;
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
 * AI中转服务商管理控制器（管理员端）
 * <p>包含子资源：服务商优势、服务商支付方式</p>
 */
@RestController
@RequestMapping("/admin/ai-relay/providers")
@RequiredArgsConstructor
public class AiRelayProviderAdminController extends AbstractAdminController {

    private final AiRelayProviderAdminService providerAdminService;

    /**
     * 分页查询服务商
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:ai-relay:provider:list")
    public R<PageResult<AiRelayProviderAdminVO>> page(@ModelAttribute AiRelayProviderPageQuery query) {
        return R.success(providerAdminService.page(query));
    }

    /**
     * 服务商详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:provider:query")
    public R<AiRelayProviderAdminVO> detail(@PathVariable Long id) {
        return R.success(providerAdminService.detail(id));
    }

    /**
     * 创建服务商
     */
    @PostMapping
    @SaCheckPermission("blog:ai-relay:provider:add")
    public R<Long> create(@RequestBody @Valid AiRelayProviderCreateRequest req) {
        return R.success(providerAdminService.create(req));
    }

    /**
     * 更新服务商
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:provider:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid AiRelayProviderUpdateRequest req) {
        providerAdminService.update(id, req);
        return R.success();
    }

    /**
     * 切换服务商上下线状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:ai-relay:provider:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        providerAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 删除服务商
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:provider:delete")
    public R<Void> delete(@PathVariable Long id) {
        providerAdminService.delete(id);
        return R.success();
    }

    // ============ 服务商优势（子资源） ============

    /**
     * 获取服务商优势列表
     */
    @GetMapping("/{providerId}/advantages")
    @SaCheckPermission("blog:ai-relay:provider:query")
    public R<List<AiRelayProviderAdvantageAdminVO>> listAdvantages(@PathVariable Long providerId) {
        return R.success(providerAdminService.listAdvantages(providerId));
    }

    /**
     * 新增服务商优势
     */
    @PostMapping("/{providerId}/advantages")
    @SaCheckPermission("blog:ai-relay:provider:edit")
    public R<Long> createAdvantage(@PathVariable Long providerId,
                                   @RequestBody @Valid AiRelayProviderAdvantageRequest req) {
        return R.success(providerAdminService.createAdvantage(providerId, req));
    }

    /**
     * 更新服务商优势
     */
    @PutMapping("/{providerId}/advantages/{advantageId}")
    @SaCheckPermission("blog:ai-relay:provider:edit")
    public R<Void> updateAdvantage(@PathVariable Long providerId,
                                   @PathVariable Long advantageId,
                                   @RequestBody @Valid AiRelayProviderAdvantageRequest req) {
        providerAdminService.updateAdvantage(providerId, advantageId, req);
        return R.success();
    }

    /**
     * 删除服务商优势
     */
    @DeleteMapping("/{providerId}/advantages/{advantageId}")
    @SaCheckPermission("blog:ai-relay:provider:edit")
    public R<Void> deleteAdvantage(@PathVariable Long providerId, @PathVariable Long advantageId) {
        providerAdminService.deleteAdvantage(providerId, advantageId);
        return R.success();
    }

    // ============ 服务商支付方式（子资源） ============

    /**
     * 获取服务商已绑定的支付方式
     */
    @GetMapping("/{providerId}/payment-methods")
    @SaCheckPermission("blog:ai-relay:provider:query")
    public R<List<AiRelayProviderPaymentMethodAdminVO>> listPaymentMethods(@PathVariable Long providerId) {
        return R.success(providerAdminService.listPaymentMethods(providerId));
    }

    /**
     * 全量绑定服务商支付方式
     */
    @PutMapping("/{providerId}/payment-methods")
    @SaCheckPermission("blog:ai-relay:provider:edit")
    public R<Void> bindPaymentMethods(@PathVariable Long providerId,
                                      @RequestBody @Valid AiRelayProviderPaymentMethodBindRequest req) {
        providerAdminService.bindPaymentMethods(providerId, req);
        return R.success();
    }
}
