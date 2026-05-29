package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI中转服务商推荐管理VO
 */
@Data
public class AiRelayProviderRecommendAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    /**
     * 冗余服务商名称（前端展示用）
     */
    private String providerName;

    private String recommendReason;

    private String reviewContent;

    private BigDecimal reviewScore;

    private String pros;

    private String cons;

    private String useScenario;

    private LocalDateTime firstUseTime;

    private LocalDateTime reviewTime;

    private LocalDateTime recommendTime;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
