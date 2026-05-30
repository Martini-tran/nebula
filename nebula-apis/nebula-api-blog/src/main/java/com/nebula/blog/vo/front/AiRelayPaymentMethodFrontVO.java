package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 中转支付方式 VO（前台）
 */
@Data
public class AiRelayPaymentMethodFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    /**
     * 图标可访问 URL（如已配置）
     */
    private String iconUrl;

    private String description;
}
