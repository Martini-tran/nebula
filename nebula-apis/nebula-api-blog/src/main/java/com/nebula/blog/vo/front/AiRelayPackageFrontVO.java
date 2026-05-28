package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 中转套餐 VO（前台）
 */
@Data
public class AiRelayPackageFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    private String providerName;

    private Long packageTypeId;

    /**
     * 套餐类型编码（day / week / month / usage）
     */
    private String packageTypeCode;

    /**
     * 套餐类型名称（天卡 / 月卡 / 按量 ...）
     */
    private String packageTypeName;

    /**
     * 计费模式：usage / subscription（沿用类型字典）
     */
    private String billingMode;

    private String name;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String currency;

    /**
     * 是否平台推荐（卡片高亮）
     */
    private Boolean recommended;

    private BigDecimal recommendScore;

    private String description;

    /**
     * 主要限额简述：取首条已上线的限制描述，缺省由 quotaAmount/quotaUnit 拼成
     */
    private String quotaSummary;

    /**
     * 套餐限制（已按 limitType 排序，仅上线项）
     */
    private List<AiRelayPackageLimitFrontVO> limits;

    /**
     * 套餐支持的模型（已按 sort 排序，仅上线项）
     */
    private List<AiRelayPackageModelFrontVO> models;

    private Integer sortOrder;
}
