package com.nebula.blog.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商推荐 创建/更新请求
 */
@Data
public class AiRelayProviderRecommendRequest {

    /**
     * 服务商ID（必填）
     */
    private Long providerId;

    /**
     * 推荐原因（精简一句话/摘要，最长1000）
     */
    private String recommendReason;

    /**
     * 完整测评内容（支持Markdown）
     */
    private String reviewContent;

    /**
     * 个人测评评分（0-10分）
     */
    private BigDecimal reviewScore;

    /**
     * 优点
     */
    private String pros;

    /**
     * 缺点
     */
    private String cons;

    /**
     * 推荐使用场景
     */
    private String useScenario;

    /**
     * 首次使用时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime firstUseTime;

    /**
     * 测评时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    /**
     * 推荐时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recommendTime;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
