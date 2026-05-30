package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转套餐限制 管理VO
 */
@Data
public class AiRelayProviderPackageLimitAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long packageId;

    private Integer limitType;

    private BigDecimal quotaAmount;

    private String quotaUnit;

    private Integer resetCycle;

    private Integer overLimitStrategy;

    private String description;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
