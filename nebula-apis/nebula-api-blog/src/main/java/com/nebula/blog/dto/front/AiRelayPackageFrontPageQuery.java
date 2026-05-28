package com.nebula.blog.dto.front;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 中转服务商套餐分页查询参数（前台）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayPackageFrontPageQuery extends PageQuery {

    /**
     * 服务商ID
     */
    private Long providerId;

    /**
     * 套餐类型ID
     */
    private Long packageTypeId;

    /**
     * 套餐类型编码（day / week / month / usage）
     */
    private String packageTypeCode;

    /**
     * 关键词（套餐名称）
     */
    private String keyword;
}
