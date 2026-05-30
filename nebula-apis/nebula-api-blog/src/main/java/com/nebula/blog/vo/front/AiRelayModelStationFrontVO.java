package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 中转「模型选择站点」行（前台）
 *
 * <p>以 ai_relay_package_model 为主表，每行=一条「套餐 × 模型」绑定，附带所属套餐、服务商（主站），
 * 以及该模型在该套餐下的输入 / 输出每百万 token 单价、消耗倍率与实付单价（= 挂牌价 × 倍率）。</p>
 */
@Data
public class AiRelayModelStationFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ---- package_model（主表） ----
    private Long id;
    private Long packageId;
    private Long modelId;
    private String providerModelCode;
    /** 消耗倍率，如 1.5 表示消耗额度 * 1.5 */
    private BigDecimal consumeMultiplier;
    private BigDecimal minChargeAmount;
    /** 最大上下文 token 数 */
    private Integer maxContextTokens;
    /** 输入挂牌单价（每百万 token），币种沿用 packageCurrency */
    private BigDecimal inputPricePerMillionTokens;
    /** 输出挂牌单价（每百万 token），币种沿用 packageCurrency */
    private BigDecimal outputPricePerMillionTokens;
    /** 输入实付单价 = input * 倍率 */
    private BigDecimal effectiveInputPricePerMillionTokens;
    /** 输出实付单价 = output * 倍率 */
    private BigDecimal effectiveOutputPricePerMillionTokens;
    private Boolean isDefault;

    // ---- model ----
    private String modelCode;
    private String modelName;
    private String modelVendor;

    // ---- package ----
    private String packageName;
    private String packageTypeCode;
    private String packageTypeName;
    private BigDecimal packagePrice;
    private BigDecimal packageOriginalPrice;
    private String packageCurrency;
    private String packageDescription;
    private Integer packageRecommended;
    private BigDecimal packageRecommendScore;

    // ---- provider（主站） ----
    private Long providerId;
    private String providerName;
    private String providerLogoText;
    private String providerLogoUrl;
    private String providerWebsiteUrl;
    private BigDecimal providerRecommendScore;
}
