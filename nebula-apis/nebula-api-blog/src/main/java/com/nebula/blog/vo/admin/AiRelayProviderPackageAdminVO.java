package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商套餐 管理VO
 */
@Data
public class AiRelayProviderPackageAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    /**
     * 服务商名称
     */
    private String providerName;

    private Long packageTypeId;

    /**
     * 套餐类型编码
     */
    private String packageTypeCode;

    /**
     * 套餐类型名称
     */
    private String packageTypeName;

    private String name;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String currency;

    private Integer isRecommended;

    private BigDecimal recommendScore;

    private String description;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
