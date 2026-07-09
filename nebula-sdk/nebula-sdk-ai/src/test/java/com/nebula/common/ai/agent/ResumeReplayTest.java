package com.nebula.common.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.flow.InMemoryFlowDefinitionRepository;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 崩溃恢复增量重放（{@link AgentEngine#resume}，阶段 4）单测：验证四铁律——
 * ① 从 snapshot + inputs 载入，重放 seq&gt;snapshot_seq 的 SUCCESS 行 apply node_result delta 恢复 context；
 * ② RETRY/FAILED 行被过滤（不把错误摘要当产物写进 context）；
 * ③ 重放只恢复产物、**不重新执行**已成功的节点（副作用计数为 0）；
 * ④ RUNNING 断点从 currentState 重跑续跑到终态；⑤ SUSPENDED 拒绝自动推进；⑥ 已终态幂等返回。
 *
 * @author nebula
 */
class ResumeReplayTest {

    /**
     * 记录每个节点被执行的次数——用于断言"重放不重新执行已成功节点"。
     */
    private static final class CountingExecutor implements FlowNodeExecutor {
        final AtomicInteger aRuns = new AtomicInteger();
        final AtomicInteger bRuns = new AtomicInteger();

        @Override
        public String type() {
            return "PROMPT";
        }

        @Override
        public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
            switch (node.getNodeCode()) {
                case "a" -> {
                    aRuns.incrementAndGet();
                    ctx.put("a_out", "A产物");
                }
                case "b" -> {
                    bRuns.incrementAndGet();
                    ctx.put("b_out", "B产物");
                }
                default -> {
                }
            }
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 线性流：a(ENTRY) → b(NORMAL) → c(TERMINAL)。
     */
    private FlowDefinition linearFlow() {
        FlowDefinition def = new FlowDefinition().setFlowCode("linear").setEngineType("STATE_MACHINE");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("a").setStateType("ENTRY").setSortNo(0));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("b").setStateType("NORMAL").setSortNo(1));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("c").setStateType("TERMINAL").setSortNo(2));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("b").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("b").setToNode("c").setSortNo(0));
        return def;
    }

    private String snapshotJson() throws Exception {
        return mapper.writeValueAsString(linearFlow());
    }

    private AgentDefinition agent() {
        return new AgentDefinition().setAgentCode("resumer").setFlowCode("linear")
                .setFlowVersion(1).setVersion(1);
    }

    private AgentEngine engine(CountingExecutor exec, InMemoryAgentInstanceStore store) {
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(linearFlow());
        FlowStateMachineFactory factory = new FlowStateMachineFactory(List.of(exec), new ConditionCompiler());
        InMemoryAgentDefinitionRepository defs = new InMemoryAgentDefinitionRepository();
        defs.register(agent());
        return new AgentEngine(flows, factory, new StateMachineOrchestrator(), store, null, mapper, defs);
    }

    /**
     * 手工塑造一个 RUNNING 断点实例：a 已成功（落 SUCCESS delta + 一行 RETRY），停在 b，未刷全量 snapshot（seq=-1）。
     */
    private String craftRunningInstanceAtB(InMemoryAgentInstanceStore store) throws Exception {
        String id = store.create(agent(), snapshotJson(), "u1", "c1", Map.of("topic", "入参主题"), null, null);
        // a 首次尝试失败（RETRY，nodeResult 是错误摘要——重放须过滤）
        store.appendRetry(id, "a", 0, 0, "TOOL_ERROR: 首次抖动");
        // a 第二次成功（SUCCESS，nodeResult 是 context delta——重放须 apply）
        store.appendSuccess(id, "a", 0, 1, Map.of("a_out", "A产物"));
        // 推进到 b（当前停在 b，尚未执行 b —— RUNNING 断点）
        store.appendTransition(id, "a", "b", 1);
        return id;
    }

    @Test
    void resumeReplaysOnlySuccessDeltaAndContinuesToTerminal() throws Exception {
        CountingExecutor exec = new CountingExecutor();
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, store);
        String id = craftRunningInstanceAtB(store);

        OrchestrationContext ctx = engine.resume(id);

        // 铁律1：inputs 注入 → topic 在
        assertEquals("入参主题", ctx.get("topic"));
        // 铁律2/3：重放 a 的 SUCCESS delta（a_out），且 a **不被重新执行**（aRuns=0）
        assertEquals("A产物", ctx.get("a_out"));
        assertEquals(0, exec.aRuns.get(), "已成功的 a 不应被重新执行（重放=恢复产物）");
        // RUNNING 断点：从 b 重跑续跑到终态（b 执行一次，写 b_out）
        assertEquals(1, exec.bRuns.get());
        assertEquals("B产物", ctx.get("b_out"));
        // 抵终态 SUCCESS
        assertEquals("SUCCESS", store.peek(id).status());
        assertEquals("c", store.peek(id).currentState());
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));
    }

    @Test
    void retryRowsAreFilteredNotAppliedAsProduct() throws Exception {
        CountingExecutor exec = new CountingExecutor();
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, store);
        String id = craftRunningInstanceAtB(store);

        OrchestrationContext ctx = engine.resume(id);

        // RETRY 行的错误摘要（"TOOL_ERROR: 首次抖动"）绝不能作为产物出现在 context 任何值里
        boolean pollutedByError = ctx.attributes().values().stream()
                .anyMatch(v -> String.valueOf(v).contains("首次抖动"));
        assertFalse(pollutedByError, "RETRY 行的错误摘要不得被当作产物写入 context");
    }

    @Test
    void suspendedInstanceRejectsResume() throws Exception {
        CountingExecutor exec = new CountingExecutor();
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, store);
        String id = store.create(agent(), snapshotJson(), "u1", "c1", Map.of(), null, null);
        store.markSuspended(id, "b", java.util.Set.of("APPROVE"), Map.of("a_out", "A产物"));

        // SUSPENDED 实例 resume 抛异常（须 signal 唤醒，不自动推进）
        OrchestrationException ex = assertThrows(OrchestrationException.class, () -> engine.resume(id));
        assertTrue(ex.getMessage().contains("SUSPENDED"));
        assertEquals(0, exec.bRuns.get(), "SUSPENDED 不应推进执行 b");
    }

    @Test
    void terminalInstanceResumeIsIdempotent() throws Exception {
        CountingExecutor exec = new CountingExecutor();
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, store);
        String id = store.create(agent(), snapshotJson(), "u1", "c1", Map.of(), null, null);
        store.appendSuccess(id, "a", 0, 0, Map.of("a_out", "A产物"));
        store.markTerminal(id, "c", Map.of("a_out", "A产物", "b_out", "B产物"));

        OrchestrationContext ctx = engine.resume(id);

        // 幂等：恢复终态 context，不重跑任何节点
        assertEquals("A产物", ctx.get("a_out"));
        assertEquals("B产物", ctx.get("b_out"));
        assertEquals(0, exec.aRuns.get());
        assertEquals(0, exec.bRuns.get());
        assertEquals("SUCCESS", store.peek(id).status());
    }
}
