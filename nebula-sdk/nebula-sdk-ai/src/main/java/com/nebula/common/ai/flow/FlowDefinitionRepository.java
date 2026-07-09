package com.nebula.common.ai.flow;

/**
 * 流程定义仓储
 * 负责按编码加载流程定义。接口语义中立，底层可由数据库、配置文件或内存实现。SDK 默认提供内存实现，
 * 业务侧可覆盖为数据库实现（参见配套 DDL：ai_flow / ai_flow_node / ai_flow_edge）。
 *
 * @author nebula
 */
public interface FlowDefinitionRepository {

    /**
     * 按编码加载流程定义
     *
     * @param flowCode 流程编码
     * @return 流程定义，不存在时返回 null
     */
    FlowDefinition findByCode(String flowCode);
}
