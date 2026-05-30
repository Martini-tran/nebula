package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI中转服务商推荐分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayProviderRecommendPageQuery extends PageQuery {

    /**
     * 服务商ID
     */
    private Long providerId;

    /**
     * 关键词（推荐原因/测评）
     */
    private String keyword;

    /**
     * 状态（1正常 0下线）
     */
    private Integer status;
}
