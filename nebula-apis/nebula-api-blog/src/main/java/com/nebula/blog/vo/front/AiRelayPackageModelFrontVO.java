package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 中转套餐模型 VO（前台）
 */
@Data
public class AiRelayPackageModelFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long packageId;

    private Long modelId;

    /**
     * 模型编码（如 claude-sonnet-4）
     */
    private String modelCode;

    private String modelName;

    /**
     * 厂商：Anthropic / OpenAI / Google ...
     */
    private String modelVendor;

    /**
     * 服务商映射后的模型编码（如 anthropic/claude-sonnet-4）
     */
    private String providerModelCode;

    /**
     * 消费倍率
     */
    private BigDecimal consumeMultiplier;

    /**
     * 单次最低消费
     */
    private BigDecimal minChargeAmount;

    /**
     * 最大上下文 token 数
     */
    private Integer maxContextTokens;

    /**
     * 输入 Token 单价（每百万 Token），币种沿用所属套餐 currency，可用于跨套餐比价
     */
    private BigDecimal inputPricePerMillionTokens;

    /**
     * 输出 Token 单价（每百万 Token），币种沿用所属套餐 currency，可用于跨套餐比价
     */
    private BigDecimal outputPricePerMillionTokens;

    /**
     * 是否套餐默认模型
     */
    private Boolean isDefault;
}
