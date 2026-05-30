package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI模型分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayModelPageQuery extends PageQuery {

    /**
     * 关键词（编码/名称）
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

    private Integer status;
}
