package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 中转套餐限制 VO（前台）
 */
@Data
public class AiRelayPackageLimitFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long packageId;

    /**
     * 限制类型编码（如 1=token / 2=request 等，由后端字典定义）
     */
    private Integer limitType;

    /**
     * 额度数量
     */
    private BigDecimal quotaAmount;

    /**
     * 额度单位（token / 次 / 万次 ...）
     */
    private String quotaUnit;

    /**
     * 重置周期（0 不重置 1 日 2 周 3 月 ...）
     */
    private Integer resetCycle;

    /**
     * 超限策略
     */
    private Integer overLimitStrategy;

    private String description;
}
