package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.dto.admin.AiRelayPaymentMethodRequest;
import com.nebula.blog.service.AiRelayPaymentMethodAdminService;
import com.nebula.blog.vo.admin.AiRelayPaymentMethodAdminVO;
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
 * AI中转支付方式 管理控制器（字典）
 */
@RestController
@RequestMapping("/admin/ai-relay/payment-methods")
@RequiredArgsConstructor
public class AiRelayPaymentMethodAdminController extends AbstractAdminController {

    private final AiRelayPaymentMethodAdminService paymentMethodAdminService;

    /**
     * 支付方式列表
     */
    @GetMapping
    @SaCheckPermission("blog:ai-relay:payment-method:list")
    public R<List<AiRelayPaymentMethodAdminVO>> list(@RequestParam(required = false) Integer status) {
        return R.success(paymentMethodAdminService.list(status));
    }

    /**
     * 支付方式详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:payment-method:query")
    public R<AiRelayPaymentMethodAdminVO> detail(@PathVariable Long id) {
        return R.success(paymentMethodAdminService.detail(id));
    }

    /**
     * 创建支付方式
     */
    @PostMapping
    @SaCheckPermission("blog:ai-relay:payment-method:add")
    public R<Long> create(@RequestBody @Valid AiRelayPaymentMethodRequest req) {
        return R.success(paymentMethodAdminService.create(req));
    }

    /**
     * 更新支付方式
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:payment-method:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid AiRelayPaymentMethodRequest req) {
        paymentMethodAdminService.update(id, req);
        return R.success();
    }

    /**
     * 切换支付方式状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:ai-relay:payment-method:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        paymentMethodAdminService.updateStatus(id, status);
        return R.success();
    }

    /**
     * 删除支付方式
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:ai-relay:payment-method:delete")
    public R<Void> delete(@PathVariable Long id) {
        paymentMethodAdminService.delete(id);
        return R.success();
    }
}
