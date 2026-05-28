package com.nebula.blog.dto.admin;

import lombok.Data;

/**
 * AI中转支付方式 创建/更新请求
 */
@Data
public class AiRelayPaymentMethodRequest {

    /**
     * 支付方式编码（必填，最长 50）
     */
    private String code;

    /**
     * 支付方式名称（必填，最长 50）
     */
    private String name;

    private Long iconFileId;

    /**
     * 支付方式说明（最长 500）
     */
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
