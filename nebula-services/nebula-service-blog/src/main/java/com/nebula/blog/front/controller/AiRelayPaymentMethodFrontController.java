package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.AiRelayPaymentMethodFrontService;
import com.nebula.blog.vo.front.AiRelayPaymentMethodFrontVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 中转支付方式字典控制器（前端）
 */
@RestController
@RequestMapping("/front/ai-relay/payment-methods")
@RequiredArgsConstructor
public class AiRelayPaymentMethodFrontController extends AbstractFrontController {

    private final AiRelayPaymentMethodFrontService paymentMethodFrontService;

    /**
     * 列出全部上线的支付方式
     */
    @GetMapping
    public R<List<AiRelayPaymentMethodFrontVO>> list() {
        return R.success(paymentMethodFrontService.listPaymentMethods());
    }
}
