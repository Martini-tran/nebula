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

    /**
     * 按图执行编排（携带执行实例上下文，支持状态持久化与断点续跑）。
     * 默认忽略 {@code runContext} 退化为 {@link #run(OrchestrationGraph, OrchestrationContext)}，
     * 使不关心持久化的实现零改动；支持续跑的实现（如 {@code DagOrchestrator} 注入了 {@link RunStateStore} 时）
     * 据此落库进度或从断点恢复。
     *
     * @param graph      编排图
     * @param ctx        编排共享上下文
     * @param runContext 执行实例上下文（runId/续跑标记）
     * @return 执行后的上下文
     */
    default OrchestrationContext run(OrchestrationGraph graph, OrchestrationContext ctx, RunContext runContext) {
        return run(graph, ctx);
    }
}
