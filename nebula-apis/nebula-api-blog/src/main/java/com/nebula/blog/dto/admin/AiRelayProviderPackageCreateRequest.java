package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转服务商套餐创建请求
 */
@Data
public class AiRelayProviderPackageCreateRequest {

    @NotNull(message = "服务商ID不能为空")
    private Long providerId;

    @NotNull(message = "套餐类型ID不能为空")
    private Long packageTypeId;

    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 100, message = "套餐名称长度不能超过100")
    private String name;

    @NotNull(message = "套餐价格不能为空")
    private BigDecimal price;

    private BigDecimal originalPrice;

    private String currency = "CNY";

    /**
     * 是否推荐（1是 0否）
     */
    private Integer isRecommended = 0;

    /**
     * 套餐推荐分
     */
    private BigDecimal recommendScore;

    @Size(max = 1000, message = "套餐说明长度不能超过1000")
    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
