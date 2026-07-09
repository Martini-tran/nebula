package com.nebula.common.ai.orchestration.statemachine;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TransitionListener} 落库回调单测：验证内核带 listener 时，正常出环/重试/失败各触发正确的回调序列，
 * 且严格遵循"先干活后记账"（SUCCESS 回调携带本步 context delta）。用记录型 fake listener 断言。
 *
 * @author nebula
 */
class TransitionListenerTest {

    private final StateMachineOrchestrator orchestrator = new StateMachineOrchestrator();

    /**
     * 记录每个回调的 fake listener
     */
    private static final class RecordingListener implements TransitionListener {
        final List<String> events = new ArrayList<>();
        final List<Map<String, Object>> deltas = new ArrayList<>();

        @Override
        public void onStateSucceeded(String instanceId, String stateCode, int seq, int attempt,
                                     Map<String, Object> contextDelta) {
            events.add("SUCCESS:" + stateCode + ":seq" + seq + ":att" + attempt);
            deltas.add(contextDelta);
        }

        @Override
        public void onStateRetry(String instanceId, String stateCode, int seq, int attempt, String errorSummary) {
            events.add("RETRY:" + stateCode + ":seq" + seq + ":att" + attempt);
        }

        @Override
        public void onTransition(String instanceId, String fromState, String toState, int seq) {
            events.add("MOVE:" + fromState + "->" + toState + ":seq" + seq);
        }

        @Override
        public void onTerminal(String instanceId, String terminalState, Map<String, Object> contextSnapshot) {
            events.add("TERMINAL:" + terminalState);
        }

        @Override
        public void onInstanceFailed(String instanceId, String failedState, String error,
                                     Map<String, Object> contextSnapshot) {
            events.add("FAILED:" + failedState);
        }
    }

    @Test
    void linearChainReportsSuccessMoveTerminalInOrder() {
        RecordingListener listener = new RecordingListener();
        StateMachineGraph graph = StateMachineGraph.builder("linear")
                .state(entry("start", ctx -> ctx.put("a", 1)))
                .state(normal("mid", ctx -> ctx.put("b", 2)))
                .state(terminal("end", ctx -> ctx.put("c", 3)))
                .transition(edge("start", "mid", null, 0))
                .transition(edge("mid", "end", null, 0))
                .build();

        orchestrator.run(graph, new OrchestrationContext(), "inst-1", listener);

        assertEquals(List.of(
                "SUCCESS:start:seq0:att0",
                "MOVE:start->mid:seq1",
                "SUCCESS:mid:seq1:att0",
                "MOVE:mid->end:seq2",
                "SUCCESS:end:seq2:att0",
                "TERMINAL:end"
        ), listener.events);
        // 先干活后记账：start 的 SUCCESS delta 应含本步写入 a=1
        assertEquals(1, listener.deltas.get(0).get("a"));
    }

    @Test
    void retryReportsRetryThenSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        StateConfig retry3 = new StateConfig(3, 0, Set.of(), 0, StateConfig.OnError.FAIL_INSTANCE, null);
        RecordingListener listener = new RecordingListener();
        StateMachineGraph graph = StateMachineGraph.builder("retry")
                .state(new StateNode("flaky", StateType.ENTRY, retry3, ctx -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IllegalStateException("transient");
                    }
                    ctx.put("done", true);
                }))
                .state(terminal("end", ctx -> { }))
                .transition(edge("flaky", "end", null, 0))
                .build();

        orchestrator.run(graph, new OrchestrationContext(), "inst-2", listener);

        // 前两次失败落 RETRY（seq0，att0/att1），第三次成功落 SUCCESS（seq0，att2）
        assertTrue(listener.events.contains("RETRY:flaky:seq0:att0"));
        assertTrue(listener.events.contains("RETRY:flaky:seq0:att1"));
        assertTrue(listener.events.contains("SUCCESS:flaky:seq0:att2"));
        assertTrue(listener.events.contains("TERMINAL:end"));
    }

    @Test
    void failureReportsInstanceFailed() {
        StateConfig retry1 = new StateConfig(1, 0, Set.of(), 0, StateConfig.OnError.FAIL_INSTANCE, null);
        RecordingListener listener = new RecordingListener();
        StateMachineGraph graph = StateMachineGraph.builder("boom")
                .state(new StateNode("boom", StateType.ENTRY, retry1, ctx -> {
                    throw new IllegalStateException("always");
                }))
                .state(terminal("end", ctx -> { }))
                .transition(edge("boom", "end", null, 0))
                .build();

        orchestrator.run(graph, new OrchestrationContext(), "inst-3", listener);

        assertTrue(listener.events.contains("FAILED:boom"));
        // 失败不应落 SUCCESS
        assertFalse(listener.events.stream().anyMatch(e -> e.startsWith("SUCCESS")));
    }

    @Test
    void onErrorGotoStateDoesNotReportSuccessForFailedState() {
        StateConfig gotoErr = new StateConfig(1, 0, Set.of(), 0, StateConfig.OnError.GOTO_STATE, "human");
        RecordingListener listener = new RecordingListener();
        StateMachineGraph graph = StateMachineGraph.builder("onerror")
                .state(new StateNode("work", StateType.ENTRY, gotoErr, ctx -> {
                    throw new IllegalStateException("need human");
                }))
                .state(normal("human", ctx -> { }))
                .state(terminal("end", ctx -> { }))
                .transition(edge("human", "end", null, 0))
                .build();

        orchestrator.run(graph, new OrchestrationContext(), "inst-4", listener);

        // work 失败错误转移到 human：不应为 work 落 SUCCESS，但应有 MOVE:work->human
        assertFalse(listener.events.contains("SUCCESS:work:seq0:att0"));
        assertTrue(listener.events.stream().anyMatch(e -> e.startsWith("MOVE:work->human")));
        assertTrue(listener.events.contains("TERMINAL:end"));
    }

    // ---- 构图辅助 ----

    private static StateNode entry(String code, OrchestrationNode behavior) {
        return new StateNode(code, StateType.ENTRY, StateConfig.NONE, behavior);
    }

    private static StateNode normal(String code, OrchestrationNode behavior) {
        return new StateNode(code, StateType.NORMAL, StateConfig.NONE, behavior);
    }

    private static StateNode terminal(String code, OrchestrationNode behavior) {
        return new StateNode(code, StateType.TERMINAL, StateConfig.NONE, behavior);
    }

    private static StateTransition edge(String from, String to,
                                        java.util.function.Predicate<OrchestrationContext> guard, int sortNo) {
        return new StateTransition(from, to, guard, null, sortNo);
    }
}
