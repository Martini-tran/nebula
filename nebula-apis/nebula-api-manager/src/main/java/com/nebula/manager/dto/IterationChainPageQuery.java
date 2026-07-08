package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 跨实例迭代链分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IterationChainPageQuery extends PageQuery {

    /**
     * 关键词（链名称/agentCode）
     */
    private String keyword;

    /**
     * 状态：ACTIVE | PAUSED | COMPLETED | FAILED
     */
    private String status;
}
