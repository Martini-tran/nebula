package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI模型档案分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelProfilePageQuery extends PageQuery {

    /**
     * 关键词（档案编码/名称/模型）
     */
    private String keyword;

    /**
     * 服务提供商标识
     */
    private String provider;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;
}
