package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI中转服务商支付方式 管理VO
 */
@Data
public class AiRelayProviderPaymentMethodAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    private Long paymentMethodId;

    /**
     * 支付方式编码
     */
    private String paymentMethodCode;

    /**
     * 支付方式名称
     */
    private String paymentMethodName;

    private String remark;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createTime;
}
