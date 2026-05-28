package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI中转服务商分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayProviderPageQuery extends PageQuery {

    /**
     * 关键词（服务商名称）
     */
    private String keyword;

    /**
     * 状态（1正常 0下线）
     */
    private Integer status;
}
