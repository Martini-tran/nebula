package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 中转服务商列表项 VO（前台）
 *
 * <p>携带卡片所需的核心字段：基础信息、推荐分、套餐摘要、模型摘要、优势、支付方式。
 * 详情接口可返回更完整的内容（如限制条目、近期更新等），但卡片场景下保持轻量。
 */
@Data
public class AiRelayProviderFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /**
     * 用于卡片占位 logo 的英文缩写（取 name 前 2 个字母大写）
     */
    private String logoText;

    /**
     * Logo 可访问 URL（如设置）
     */
    private String logoUrl;

    private String websiteUrl;

    private String description;

    /**
     * 综合推荐分
     */
    private BigDecimal recommendScore;

    private Integer sortOrder;

    /**
     * 套餐列表（已按上线状态过滤，按推荐 / 排序）
     */
    private List<AiRelayPackageFrontVO> packages;

    /**
     * 服务商优势（已按 sort 排序，仅上线项）
     */
    private List<AiRelayProviderAdvantageFrontVO> advantages;

    /**
     * 支持的支付方式（已按 sort 排序，仅上线项）
     */
    private List<AiRelayPaymentMethodFrontVO> paymentMethods;

    /**
     * 套餐覆盖到的模型集合（去重，按厂商 + 模型名）
     */
    private List<AiRelayModelFrontVO> models;

    /**
     * 套餐覆盖到的模型厂商（claude / gpt / gemini 等，去重）
     */
    private List<String> vendorTypes;

    /**
     * 套餐覆盖到的计费模式集合（usage / subscription）
     */
    private List<String> billingModes;
}
