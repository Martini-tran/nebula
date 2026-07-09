package com.nebula.manager.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.ai.webhook.WebhookDelivery;
import com.nebula.common.ai.webhook.WebhookDeliveryStore;
import com.nebula.common.ai.webhook.WebhookDispatcher;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.R;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编排回调投递管理控制器（管理员端）
 * W1 提供：查投递记录 + <b>手动重发</b>——补上"自动重发扫描器（W2）"未落地前的空窗（见 docs/编排回调Webhook设计.md 5.4）。
 * 重发只重发回调、不重跑 Agent（payload 已在 delivery 记录里）。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-webhook")
@RequiredArgsConstructor
public class WebhookAdminController {

    private final WebhookDeliveryStore deliveryStore;

    private final WebhookDispatcher dispatcher;

    /**
     * 查一条投递记录（含 payload / status / 失败原因，用于排障）
     */
    @GetMapping("/deliveries/{deliveryId}")
    @SaCheckPermission("manager:ai-webhook:query")
    public R<WebhookDelivery> detail(@PathVariable String deliveryId) {
        WebhookDelivery delivery = deliveryStore.load(deliveryId);
        if (delivery == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "投递记录不存在: " + deliveryId);
        }
        return R.success(delivery);
    }

    /**
     * 手动重发一条投递（含 FAILED / DEAD 的）：读回 payload 重发，不重跑 Agent。
     */
    @PostMapping("/deliveries/{deliveryId}/retry")
    @SaCheckPermission("manager:ai-webhook:retry")
    public R<Boolean> retry(@PathVariable String deliveryId) {
        if (!StringUtils.hasText(deliveryId)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "投递标识不能为空");
        }
        if (deliveryStore.load(deliveryId) == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "投递记录不存在: " + deliveryId);
        }
        boolean ok = dispatcher.redeliver(deliveryId);
        return R.success(ok ? "重发成功" : "重发失败（已落 FAILED，可再试）", ok);
    }
}
