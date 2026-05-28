package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * AI中转服务商支付方式 批量绑定请求
 */
@Data
public class AiRelayProviderPaymentMethodBindRequest {

    /**
     * 支付方式ID列表（全量覆盖绑定）
     */
    @NotNull(message = "支付方式ID列表不能为空")
    private List<Long> paymentMethodIds;
}
