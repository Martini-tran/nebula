package com.nebula.common.ai.orchestration;

/**
 * 编排节点（函数式）
 * 表达「做什么」的最小单元：读取并写回{@link OrchestrationContext}。节点本身不声明依赖，
 * 节点间的先后与条件由{@link OrchestrationGraph}的边描述。节点内部自行决定调用哪个 Agent、
 * 是否循环等业务逻辑。
 *
 * @author nebula
 */
@FunctionalInterface
public interface OrchestrationNode {

    /**
     * 执行节点逻辑
     *
     * @param ctx 编排共享上下文
     */
    void execute(OrchestrationContext ctx);
}
