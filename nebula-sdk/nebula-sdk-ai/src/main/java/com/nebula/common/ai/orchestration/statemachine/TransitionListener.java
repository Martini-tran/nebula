package com.nebula.common.ai.orchestration.statemachine;

import java.util.Map;

/**
 * 状态机转移监听器（落库/审计 SPI）
 * {@link StateMachineOrchestrator} 在推进过程中的各「记账点」回调本接口，把纯内存的状态推进变成可落库/可回放。
 * 内核只负责在既有控制流的记账处触发回调，<b>不感知</b>具体落库实现——数据库版由 {@code AgentInstanceStore} 承接
 * （见 {@code StoreBackedTransitionListener}），缺省用 {@link #NOOP} 保持阶段 1 的纯内存行为。
 *
 * <p><b>先干活后记账（铁律 4）</b>：内核保证节点产物先 apply 到 context，再触发 {@link #onStateSucceeded}。
 * 因此「transition 里有 SUCCESS 记录 ⇒ 该步一定已干成」，崩溃恢复时可安全重放 SUCCESS 行。
 *
 * <p><b>分级落盘（决策 v3）</b>：RUNNING 中普通转移只 append 轻量 transition（{@link #onStateSucceeded}/
 * {@link #onTransition}），{@code context_snapshot} 不刷；全量刷仅在终态（{@link #onTerminal}/
 * {@link #onInstanceFailed}）——阶段 3 挂起时在同类回调追加全量刷即可。
 *
 * <p>全部方法给 default 空实现，便于实现方按需覆盖、后续阶段增量扩展（如挂起回调）。
 *
 * @author nebula
 */
public interface TransitionListener {

    /**
     * 什么都不做的监听器：内核无落库需求时（阶段 1 行为）使用。
     */
    TransitionListener NOOP = new TransitionListener() {
    };

    /**
     * 节点执行成功（已 apply 产物到 context）后回调：落一行 {@code outcome=SUCCESS} 的 transition。
     *
     * @param instanceId   实例标识（纯内存执行时为 null）
     * @param stateCode    刚成功的状态编码
     * @param seq          转移序号（从 0 递增，重试也占序号）
     * @param attempt      成功时的尝试次数（0=首次成功）
     * @param contextDelta 本步对 context 的变更集（该节点写入的 kv），用于增量重放恢复产物
     */
    default void onStateSucceeded(String instanceId, String stateCode, int seq, int attempt,
                                  Map<String, Object> contextDelta) {
    }

    /**
     * 节点某次尝试失败但仍将重试时回调：落一行 {@code outcome=RETRY} 的 transition（node_result 记错误摘要）。
     *
     * @param instanceId   实例标识
     * @param stateCode    失败的状态编码
     * @param seq          转移序号
     * @param attempt      失败的尝试次数
     * @param errorSummary 错误摘要（非产物；重放时按 outcome 过滤，不回写 context）
     */
    default void onStateRetry(String instanceId, String stateCode, int seq, int attempt, String errorSummary) {
    }

    /**
     * 状态推进（含成功后正常转移与失败错误转移）时回调：更新 current_state 与 transition_count。
     *
     * @param instanceId 实例标识
     * @param fromState  源状态
     * @param toState    目标状态
     * @param seq        本次转移序号
     */
    default void onTransition(String instanceId, String fromState, String toState, int seq) {
    }

    /**
     * 到达终态、实例 SUCCESS 时回调：标记终态并全量刷 context_snapshot。
     *
     * @param instanceId      实例标识
     * @param terminalState   终态编码
     * @param contextSnapshot 全量产物快照
     */
    default void onTerminal(String instanceId, String terminalState, Map<String, Object> contextSnapshot) {
    }

    /**
     * 实例 FAILED 时回调：标记失败并全量刷 context_snapshot。
     *
     * @param instanceId      实例标识
     * @param failedState     失败时所在状态（可能为 null，如超转移上限）
     * @param error           失败原因
     * @param contextSnapshot 失败时的产物快照
     */
    default void onInstanceFailed(String instanceId, String failedState, String error,
                                  Map<String, Object> contextSnapshot) {
    }
}
