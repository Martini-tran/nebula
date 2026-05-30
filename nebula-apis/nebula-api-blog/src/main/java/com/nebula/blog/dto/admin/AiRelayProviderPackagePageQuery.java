package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI中转服务商套餐分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayProviderPackagePageQuery extends PageQuery {

    /**
     * 服务商ID
     */
    private Long providerId;

    /**
     * 套餐类型ID
     */
    private Long packageTypeId;

    /**
     * 关键词（套餐名称）
     */
    private String keyword;

    /**
     * 状态（1正常 0下线）
     */
    private Integer status;
}
