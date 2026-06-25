package com.nebula.common.ai.orchestration;

/**
 * 编排器
 * 按给定{@link OrchestrationGraph}驱动各节点执行，是编排执行策略的抽象。默认实现
 * {@link DagOrchestrator}按拓扑序单线程执行；并行/其他策略实现同一接口即可替换。
 *
 * @author nebula
 */
public interface Orchestrator {

    /**
     * 按图执行编排
     *
     * @param graph 编排图
     * @param ctx   编排共享上下文（执行结果就地写入并返回）
     * @return 执行后的上下文
     */
    OrchestrationContext run(OrchestrationGraph graph, OrchestrationContext ctx);
}
