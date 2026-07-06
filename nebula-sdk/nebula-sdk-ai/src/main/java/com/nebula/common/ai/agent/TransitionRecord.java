package com.nebula.common.ai.agent;

import java.util.Map;

/**
 * 一条状态转移记录（回放/崩溃恢复重放读侧值对象，阶段 4）
 * 对应 {@code ai_agent_instance_transition} 一行。{@code outcome} 区分 SUCCESS/RETRY/FAILED——
 * <b>崩溃恢复增量重放只 apply outcome=SUCCESS 行的 {@link #nodeResult}</b>（铁律 2：RETRY/FAILED 行的
 * nodeResult 存错误摘要不是产物，重放它们会把错误当产物写进 context）。
 *
 * @param seq        转移序号（从 0 递增，重试也占号）
 * @param fromState  源状态（可空）
 * @param toState    目标状态
 * @param eventName  触发事件名（阶段 3 signal 唤醒用，可空）
 * @param attempt    尝试次数（0=首次）
 * @param outcome    结果：SUCCESS | RETRY | FAILED | COMPENSATED
 * @param nodeResult 该步对 context 的变更集（SUCCESS 行为产物 delta；RETRY/FAILED 行为错误摘要，重放时须过滤）
 *
 * @author nebula
 */
public record TransitionRecord(
        int seq,
        String fromState,
        String toState,
        String eventName,
        int attempt,
        String outcome,
        Map<String, Object> nodeResult) {

    /**
     * 是否为成功轨迹（增量重放只用这类行恢复产物）
     *
     * @return outcome=SUCCESS 时 true
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(outcome);
    }
}
