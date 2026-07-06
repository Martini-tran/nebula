package com.nebula.common.ai.agent;

import com.nebula.common.ai.orchestration.statemachine.TransitionListener;

import java.util.Map;

/**
 * 落库版转移监听器
 * 把 {@link StateMachineOrchestrator} 的转移回调转成 {@link AgentInstanceStore} 的写调用——内核与 store 之间的胶水。
 * SDK 层只依赖 store 接口，不感知具体是内存还是数据库实现。
 *
 * <p>回调到写方法的映射一一对应，落库时序即内核触发时序（先干活后记账在内核侧已保证）。
 *
 * @author nebula
 */
public class StoreBackedTransitionListener implements TransitionListener {

    private final AgentInstanceStore store;

    public StoreBackedTransitionListener(AgentInstanceStore store) {
        this.store = store;
    }

    @Override
    public void onStateSucceeded(String instanceId, String stateCode, int seq, int attempt,
                                 Map<String, Object> contextDelta) {
        store.appendSuccess(instanceId, stateCode, seq, attempt, contextDelta);
    }

    @Override
    public void onStateRetry(String instanceId, String stateCode, int seq, int attempt, String errorSummary) {
        store.appendRetry(instanceId, stateCode, seq, attempt, errorSummary);
    }

    @Override
    public void onTransition(String instanceId, String fromState, String toState, int seq) {
        store.appendTransition(instanceId, fromState, toState, seq);
    }

    @Override
    public void onTerminal(String instanceId, String terminalState, Map<String, Object> contextSnapshot) {
        store.markTerminal(instanceId, terminalState, contextSnapshot);
    }

    @Override
    public void onInstanceFailed(String instanceId, String failedState, String error,
                                 Map<String, Object> contextSnapshot) {
        store.markFailed(instanceId, failedState, error, contextSnapshot);
    }
}
