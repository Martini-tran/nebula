package com.nebula.common.ai.orchestration.statemachine;

import com.nebula.common.ai.orchestration.OrchestrationException;

/**
 * 状态执行超时异常
 * 状态节点执行超过其 {@code stateConfig.timeoutMs} 时抛出。错误类型归为 {@code TIMEOUT}，
 * 可被 {@code retry.on:["TIMEOUT"]} 命中触发重试（对齐文档 4·5.2 示例）。
 *
 * @author nebula
 */
public class StateTimeoutException extends OrchestrationException {

    public StateTimeoutException(String message) {
        super(message);
    }
}
