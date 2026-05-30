package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 中转服务商推荐 VO（前台）
 *
 * <p>携带前台推荐卡片所需的核心字段：服务商基础信息、推荐原因、测评摘要、评分、优缺点等。
 * 同时附带服务商基础信息（logo / 官网 / 简介），便于前端一次性渲染卡片。
 */
@Data
public class AiRelayRecommendFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 推荐ID */
    private Long id;

    /** 服务商ID */
    private Long providerId;

    /** 服务商名称 */
    private String providerName;

    /** 服务商 Logo 缩写（取 name 前两个字符） */
    private String providerLogoText;

    /** 服务商 Logo URL（如设置） */
    private String providerLogoUrl;

    /** 服务商官网 */
    private String websiteUrl;

    /** 服务商简介 */
    private String providerDescription;

    /** 推荐原因（一句话） */
    private String recommendReason;

    /** 完整测评内容（支持 Markdown） */
    private String reviewContent;

    /** 个人测评评分（0-10） */
    private BigDecimal reviewScore;

    /** 优点（多个用换行/分号分隔） */
    private String pros;

    /** 缺点（多个用换行/分号分隔） */
    private String cons;

    /** 推荐使用场景 */
    private String useScenario;

    /** 首次使用时间 */
    private LocalDateTime firstUseTime;

    /** 测评时间 */
    private LocalDateTime reviewTime;

    /** 推荐时间 */
    private LocalDateTime recommendTime;

    /** 展示排序 */
    private Integer sortOrder;

    /** 累计充值次数 */
    private Integer rechargeCount;

    /** 累计折合人民币金额 */
    private BigDecimal totalCnyAmount;

    /** 最近一次充值时间 */
    private LocalDateTime lastRechargeTime;
}
