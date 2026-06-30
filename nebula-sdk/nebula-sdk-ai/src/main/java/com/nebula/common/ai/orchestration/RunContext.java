package com.nebula.common.ai.orchestration;

/**
 * 编排执行实例上下文
 * 承载一次编排执行的实例标识与续跑标记，由 {@code FlowEngine} 构建并下传给 {@link Orchestrator}，
 * 使编排器知道「本次执行对应哪个 runId、是否从断点续跑」，据此向 {@link RunStateStore} 落库或恢复进度。
 *
 * @param runId   执行实例唯一标识；为空表示不启用状态持久化（退化为纯内存执行）
 * @param flowCode 流程编码
 * @param version  流程版本
 * @param resume   是否为续跑：true 时编排器先从 store 恢复已执行节点集与产物快照，跳过已完成节点
 * @author nebula
 */
public record RunContext(String runId, String flowCode, int version, boolean resume) {

    /**
     * 是否启用状态持久化（runId 非空）
     *
     * @return 是否持久化
     */
    public boolean persistent() {
        return runId != null && !runId.isBlank();
    }
}
