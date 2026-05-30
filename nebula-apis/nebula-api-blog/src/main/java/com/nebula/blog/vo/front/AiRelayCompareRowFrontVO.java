package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 中转比价行（前台）
 *
 * <p>以 ai_relay_provider_package_limit 为主表，每行=一条限额，附带所属套餐、服务商
 * 与（可选的）所选模型在该套餐下的输入 / 输出单价（含倍率）。</p>
 */
@Data
public class AiRelayCompareRowFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ---- limit ----
    private Long limitId;
    private Integer limitType;
    private String limitTypeText;
    private BigDecimal quotaAmount;
    private String quotaUnit;
    private Integer resetCycle;
    private String resetCycleText;
    private Integer overLimitStrategy;
    private String overLimitStrategyText;
    private String limitDescription;

    // ---- package ----
    private Long packageId;
    private String packageName;
    private String packageTypeCode;
    private String packageTypeName;
    private BigDecimal packagePrice;
    private BigDecimal packageOriginalPrice;
    private String packageCurrency;
    private String packageDescription;
    private Integer packageRecommended;
    private BigDecimal packageRecommendScore;

    // ---- provider ----
    private Long providerId;
    private String providerName;
    private String providerLogoText;
    private String providerLogoUrl;
    private String providerWebsiteUrl;
    private BigDecimal providerRecommendScore;

    // ---- model（仅在 modelId 指定时填充） ----
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String modelVendor;
    private String providerModelCode;
    /** 消耗倍率 */
    private BigDecimal consumeMultiplier;
    /** 输入挂牌单价（每百万 token），币种沿用 packageCurrency */
    private BigDecimal inputPricePerMillionTokens;
    /** 输出挂牌单价（每百万 token），币种沿用 packageCurrency */
    private BigDecimal outputPricePerMillionTokens;
    /** 输入实付单价 = input * 倍率 */
    private BigDecimal effectiveInputPricePerMillionTokens;
    /** 输出实付单价 = output * 倍率 */
    private BigDecimal effectiveOutputPricePerMillionTokens;
    /** 最大上下文 token 数 */
    private Integer maxContextTokens;
}
