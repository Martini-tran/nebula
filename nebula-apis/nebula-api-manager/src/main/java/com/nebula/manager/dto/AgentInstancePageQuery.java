package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 智能体实例分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentInstancePageQuery extends PageQuery {

    /**
     * 归属 Agent 编码
     */
    private String agentCode;

    /**
     * 实例状态：RUNNING | SUSPENDED | SUCCESS | FAILED
     */
    private String status;

    /**
     * 归属用户ID
     */
    private String userId;
}
