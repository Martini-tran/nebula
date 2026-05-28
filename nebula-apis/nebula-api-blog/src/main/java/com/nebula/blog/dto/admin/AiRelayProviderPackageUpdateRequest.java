package com.nebula.blog.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI中转服务商套餐更新请求
 */
@Data
public class AiRelayProviderPackageUpdateRequest {

    private Long packageTypeId;

    @Size(max = 100, message = "套餐名称长度不能超过100")
    private String name;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String currency;

    private Integer isRecommended;

    private BigDecimal recommendScore;

    @Size(max = 1000, message = "套餐说明长度不能超过1000")
    private String description;

    private Integer sortOrder;

    private Integer status;
}
