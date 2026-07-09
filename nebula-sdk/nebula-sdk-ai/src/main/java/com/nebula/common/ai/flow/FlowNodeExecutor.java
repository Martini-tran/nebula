package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;

/**
 * 流程节点执行器（SPI）
 * 按节点类型实现「该类节点如何执行」。{@link FlowGraphFactory} 据 {@code node.getNodeType()} 选取
 * 对应执行器，把节点包装为编排图中的一个可执行单元。新增节点类型（SCRIPT/HTTP/SUBFLOW 等）只需新增
 * 一个执行器并注册为 Bean，无需改动引擎。
 *
 * @author nebula
 */
public interface FlowNodeExecutor {

    /**
     * 支持的节点类型，与 {@link FlowNodeDefinition#getNodeType()} 对应
     *
     * @return 节点类型
     */
    String type();

    /**
     * 执行节点：读取并写回编排上下文
     *
     * @param node 节点定义
     * @param ctx  编排共享上下文
     */
    void execute(FlowNodeDefinition node, OrchestrationContext ctx);
}
