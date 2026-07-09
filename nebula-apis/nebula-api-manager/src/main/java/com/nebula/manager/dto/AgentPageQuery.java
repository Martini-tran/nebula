package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 智能体分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentPageQuery extends PageQuery {

    /**
     * 关键词（Agent 编码/名称）
     */
    private String keyword;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;
}
