package com.nebula.common.ai.orchestration.statemachine;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机内核控制流单测：覆盖阶段 1 承诺的起步/裁决/记账 + 回跳/成环/重试/超时/错误转移/max_transitions。
 * 用手工构图（不经 Flow 定义），聚焦内核推进逻辑本身。
 *
 * @author nebula
 */
class StateMachineOrchestratorTest {

    private final StateMachineOrchestrator orchestrator =
            new StateMachineOrchestrator(Executors.newCachedThreadPool());

    /**
     * 线性链：entry -> mid -> terminal，顺序执行，正常收尾（无失败键）。
     */
    @Test
    void linearChainReachesTerminal() {
        List<String> order = new ArrayList<>();
        StateMachineGraph graph = StateMachineGraph.builder("linear")
                .state(entry("start", ctx -> order.add("start")))
                .state(normal("mid", ctx -> order.add("mid")))
                .state(terminal("end", ctx -> order.add("end")))
                .transition(edge("start", "mid", null, 0))
                .transition(edge("mid", "end", null, 0))
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());

        assertEquals(List.of("start", "mid", "end"), order);
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));
    }

    /**
     * 自我审查成环：生成 -> 评分 -> (score<阈值 回跳生成 / score>=阈值 出环到终态)。
     * 生成节点每轮递增 score，验证回跳重生成直到出环。
     */
    @Test
    void selfReviewLoopExitsWhenScoreReached() {
        AtomicInteger generateCount = new AtomicInteger();
        StateMachineGraph graph = StateMachineGraph.builder("self-review")
                .state(entry("generate", ctx -> {
                    int c = generateCount.incrementAndGet();
                    ctx.put("score", c * 3); // 3, 6, 9...
                }))
                .state(normal("review", ctx -> { }))
                .state(terminal("output", ctx -> { }))
                // sortNo 升序裁决：先判 score<8 回跳，否则 default 出环
                .transition(edge("review", "generate", ctxScoreLt(8), 0))
                .transition(edge("review", "output", null, 1))
                .transition(edge("generate", "review", null, 0))
                .build();

        orchestrator.run(graph, new OrchestrationContext());

        // score 序列 3(<8回跳)、6(<8回跳)、9(>=8出环) → 生成 3 次
        assertEquals(3, generateCount.get());
    }

    /**
     * max_transitions 兜底：guard 永真成死循环，超上限强制 FAILED。
     */
    @Test
    void exceedingMaxTransitionsFailsInstance() {
        StateMachineGraph graph = StateMachineGraph.builder("infinite")
                .maxTransitions(5)
                .state(entry("a", ctx -> { }))
                .state(normal("b", ctx -> { }))
                .state(terminal("end", ctx -> { })) // 满足 TERMINAL≥1 校验，但永不到达
                .transition(edge("a", "b", null, 0))
                .transition(edge("b", "a", null, 0)) // 成环，永不出环
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());

        String error = (String) ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY);
        assertTrue(error != null && error.contains("max_transitions"), "应因超转移上限失败: " + error);
    }

    /**
     * 节点重试：前两次抛异常，第三次成功。maxAttempts=3，验证原地重试后正常推进到终态。
     */
    @Test
    void nodeRetriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        StateConfig retry3 = new StateConfig(3, 0, Set.of(), 0, StateConfig.OnError.FAIL_INSTANCE, null);
        StateMachineGraph graph = StateMachineGraph.builder("retry")
                .state(new StateNode("flaky", StateType.ENTRY, retry3, ctx -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IllegalStateException("transient");
                    }
                }))
                .state(terminal("end", ctx -> { }))
                .transition(edge("flaky", "end", null, 0))
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());

        assertEquals(3, attempts.get());
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));
    }

    /**
     * 重试耗尽仍失败：maxAttempts=2，节点恒抛异常，onError=FAIL_INSTANCE → 实例 FAILED。
     */
    @Test
    void retryExhaustedFailsInstance() {
        AtomicInteger attempts = new AtomicInteger();
        StateConfig retry2 = new StateConfig(2, 0, Set.of(), 0, StateConfig.OnError.FAIL_INSTANCE, null);
        StateMachineGraph graph = StateMachineGraph.builder("retry-fail")
                .state(new StateNode("always-boom", StateType.ENTRY, retry2, ctx -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("boom");
                }))
                .state(terminal("end", ctx -> { }))
                .transition(edge("always-boom", "end", null, 0))
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());

        assertEquals(2, attempts.get()); // 首次 + 1 次重试
        assertTrue(((String) ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY)).contains("always-boom"));
    }

    /**
     * 错误转移 onError=GOTO_STATE：节点失败跳到 errorState（人工审核），复用转移机制到达终态。
     */
    @Test
    void onErrorGotoStateRoutesToErrorState() {
        List<String> visited = new ArrayList<>();
        StateConfig gotoErr = new StateConfig(1, 0, Set.of(), 0, StateConfig.OnError.GOTO_STATE, "human_review");
        StateMachineGraph graph = StateMachineGraph.builder("onerror-goto")
                .state(new StateNode("work", StateType.ENTRY, gotoErr, ctx -> {
                    visited.add("work");
                    throw new IllegalStateException("need human");
                }))
                .state(normal("human_review", ctx -> visited.add("human_review")))
                .state(terminal("end", ctx -> visited.add("end")))
                .transition(edge("human_review", "end", null, 0))
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());

        assertEquals(List.of("work", "human_review", "end"), visited);
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));
    }

    /**
     * 裁决零匹配：非终态出边 guard 全 false 且无 default → 实例 FAILED（不静默停住）。
     */
    @Test
    void noMatchingTransitionFailsInstance() {
        StateMachineGraph graph = StateMachineGraph.builder("deadend")
                .state(entry("a", ctx -> { }))
                .state(normal("b", ctx -> { }))
                .state(terminal("end", ctx -> { }))
                .transition(edge("a", "b", ctx -> false, 0)) // 唯一出边 guard 恒 false
                .transition(edge("b", "end", null, 0))
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());

        String error = (String) ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY);
        assertTrue(error != null && error.contains("no matching transition"), "应因无匹配转移失败: " + error);
    }

    /**
     * 超时熔断：节点睡眠超过 timeoutMs → 判 TIMEOUT 失败。配 retry.on=[TIMEOUT] 一次后放弃 → 实例 FAILED。
     */
    @Test
    void timeoutTriggersFailure() {
        StateConfig timeout = new StateConfig(1, 0, Set.of(), 50, StateConfig.OnError.FAIL_INSTANCE, null);
        StateMachineGraph graph = StateMachineGraph.builder("timeout")
                .state(new StateNode("slow", StateType.ENTRY, timeout, ctx -> sleep(500)))
                .state(terminal("end", ctx -> { }))
                .transition(edge("slow", "end", null, 0))
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());

        assertTrue(((String) ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY)).contains("slow"));
    }

    /**
     * 裁决短路：多条 guard 同时为真时，取 sortNo 最小的首个命中，后续不再求值。
     */
    @Test
    void arbitrationTakesFirstMatchBySortNo() {
        List<String> reached = new ArrayList<>();
        StateMachineGraph graph = StateMachineGraph.builder("first-match")
                .state(entry("a", ctx -> { }))
                .state(terminal("high", ctx -> reached.add("high")))
                .state(terminal("low", ctx -> reached.add("low")))
                .transition(edge("a", "high", ctx -> true, 0)) // 更小 sortNo，先命中
                .transition(edge("a", "low", ctx -> true, 1))  // 也真，但被短路
                .build();

        orchestrator.run(graph, new OrchestrationContext());

        assertEquals(List.of("high"), reached);
        assertFalse(reached.contains("low"));
    }

    // ---- 构图辅助 ----

    private static StateNode entry(String code, com.nebula.common.ai.orchestration.OrchestrationNode behavior) {
        return new StateNode(code, StateType.ENTRY, StateConfig.NONE, behavior);
    }

    private static StateNode normal(String code, com.nebula.common.ai.orchestration.OrchestrationNode behavior) {
        return new StateNode(code, StateType.NORMAL, StateConfig.NONE, behavior);
    }

    private static StateNode terminal(String code, com.nebula.common.ai.orchestration.OrchestrationNode behavior) {
        return new StateNode(code, StateType.TERMINAL, StateConfig.NONE, behavior);
    }

    private static StateTransition edge(String from, String to,
                                        java.util.function.Predicate<OrchestrationContext> guard, int sortNo) {
        return new StateTransition(from, to, guard, null, sortNo);
    }

    private static java.util.function.Predicate<OrchestrationContext> ctxScoreLt(int threshold) {
        return ctx -> {
            Object s = ctx.get("score");
            return s instanceof Number n && n.intValue() < threshold;
        };
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
