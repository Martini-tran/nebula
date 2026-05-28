package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI中转支付方式 创建/更新请求
 */
@Data
public class AiRelayPaymentMethodRequest {

    @NotBlank(message = "支付方式编码不能为空")
    @Size(max = 50, message = "支付方式编码长度不能超过50")
    private String code;

    @NotBlank(message = "支付方式名称不能为空")
    @Size(max = 50, message = "支付方式名称长度不能超过50")
    private String name;

    private Long iconFileId;

    @Size(max = 500, message = "支付方式说明长度不能超过500")
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
