package com.nebula.common.ai.flow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存流程定义仓储
 * SDK 默认实现，支持编程式注册。无存储依赖，便于无数据库场景与单元测试；业务侧可声明数据库实现覆盖。
 *
 * @author nebula
 */
public class InMemoryFlowDefinitionRepository implements FlowDefinitionRepository {

    private final Map<String, FlowDefinition> flows = new ConcurrentHashMap<>();

    /**
     * 注册一个流程定义
     *
     * @param flow 流程定义
     * @return 当前仓储，便于链式注册
     */
    public InMemoryFlowDefinitionRepository register(FlowDefinition flow) {
        if (flow != null && flow.getFlowCode() != null) {
            flows.put(flow.getFlowCode(), flow);
        }
        return this;
    }

    @Override
    public FlowDefinition findByCode(String flowCode) {
        return flowCode == null ? null : flows.get(flowCode);
    }
}
