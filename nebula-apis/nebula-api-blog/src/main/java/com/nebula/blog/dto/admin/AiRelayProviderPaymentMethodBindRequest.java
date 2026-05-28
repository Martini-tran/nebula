package com.nebula.blog.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * AI中转服务商支付方式 批量绑定请求
 */
@Data
public class AiRelayProviderPaymentMethodBindRequest {

    /**
     * 支付方式ID列表（必填，全量覆盖绑定）
     */
    private List<Long> paymentMethodIds;
}
