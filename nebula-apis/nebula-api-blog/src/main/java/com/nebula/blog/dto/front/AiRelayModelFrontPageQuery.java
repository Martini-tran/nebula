package com.nebula.blog.dto.front;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 模型分页查询参数（前台）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayModelFrontPageQuery extends PageQuery {

    /**
     * 关键词（编码 / 名称）
     */
    private String keyword;

    /**
     * 模型厂商
     */
    private String modelVendor;

    /**
     * 模型类型
     */
    private Integer modelType;
}
