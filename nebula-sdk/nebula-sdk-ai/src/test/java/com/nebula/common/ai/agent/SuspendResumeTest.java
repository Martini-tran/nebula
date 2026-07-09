package com.nebula.common.ai.agent;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挂起/唤醒（Human-in-the-loop，阶段 3）端到端单测：验证
 * ① {@code stateConfig.suspend=true} 审批节点成功后主动挂起，实例 status=SUSPENDED、落 awaiting_events、
 *    context_snapshot 全量刷、未置 FAILED/SUCCESS；
 * ② {@code signal(id,event,payload)} 命中 awaiting_events → 从挂起态续跑，payload 参与出边 guard 裁决
 *    （approve→出环 output / reject→回跳 generate）；
 * ③ {@code onError=SUSPEND} 节点失败挂起；
 * ④ 事件不命中 awaiting_events → 拒绝，保留挂起态。
 *
 * @author nebula
 */
class SuspendResumeTest {

    /**
     * 生成器：generate 节点执行时把 draft 写入 context（模拟产出草稿）。
     */
    private static final class GenExecutor implements FlowNodeExecutor {
        int gens = 0;
        boolean reviewFails = false;

        @Override
        public String type() {
            return "PROMPT";
        }

        @Override
        public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
            if ("generate".equals(node.getNodeCode())) {
                ctx.put("draft", "草稿v" + (++gens));
            } else if ("review".equals(node.getNodeCode()) && reviewFails) {
                throw new IllegalStateException("审查执行异常");
            }
        }
    }

    private FlowStateMachineFactory factory(GenExecutor exec) {
        return new FlowStateMachineFactory(List.of(exec), new ConditionCompiler());
    }

    /**
     * 审批循环流：generate → review（成功后主动挂起等 approve/reject）→ approve 出环 output / reject 回跳 generate。
     */
    private FlowDefinition approvalFlow() {
        FlowDefinition def = new FlowDefinition().setFlowCode("approval").setEngineType("STATE_MACHINE");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("generate").setStateType("ENTRY").setSortNo(0));
        FlowNodeDefinition review = new FlowNodeDefinition().setNodeCode("review").setStateType("NORMAL").setSortNo(1);
        // 成功后主动挂起，等待 approve/reject 事件
        review.getNodeConfig().put("stateConfig", Map.of(
                "suspend", true,
                "awaitingEvents", List.of("approve", "reject")));
        def.getNodes().add(review);
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("output").setStateType("TERMINAL").setSortNo(2));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("generate").setToNode("review").setSortNo(0));
        // review 出边按 signal 事件裁决：approve→output（出环），reject→generate（回跳）
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("output")
                .setConditionExpr("get('__signalEvent') == 'approve'").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("generate")
                .setConditionExpr("get('__signalEvent') == 'reject'").setSortNo(1));
        return def;
    }

    private AgentDefinition approvalAgent() {
        return new AgentDefinition().setAgentCode("approver").setFlowCode("approval")
                .setFlowVersion(1).setVersion(1);
    }

    private AgentEngine engine(GenExecutor exec, InMemoryFlowDefinitionRepository flows,
                               InMemoryAgentInstanceStore store) {
        InMemoryAgentDefinitionRepository defs = new InMemoryAgentDefinitionRepository();
        defs.register(approvalAgent());
        return new AgentEngine(flows, factory(exec), new StateMachineOrchestrator(),
                store, null, null, defs);
    }

    @Test
    void suspendOnSuccessThenApproveResumesToTerminal() {
        GenExecutor exec = new GenExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(approvalFlow());
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, flows, store);

        // 首次跑到 review 主动挂起
        OrchestrationContext ctx = engine.run(approvalAgent(), Map.of(), "u1", "c1");
        String instanceId = only(store);
        InMemoryAgentInstanceStore.Instance inst = store.peek(instanceId);

        assertEquals("SUSPENDED", inst.status());
        assertEquals("review", inst.currentState());
        assertTrue(inst.awaitingEvents().contains("APPROVE"));
        assertTrue(inst.awaitingEvents().contains("REJECT"));
        // 挂起时全量刷了 context_snapshot（draft 已在其中），且非终态非失败
        assertEquals("草稿v1", inst.contextSnapshot().get("draft"));
        assertNotNull(ctx.get(StateMachineOrchestrator.SUSPENDED_KEY));
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));

        // signal approve → 续跑出环到 output，实例 SUCCESS
        OrchestrationContext resumed = engine.signal(instanceId, "approve", Map.of());
        InMemoryAgentInstanceStore.Instance after = store.peek(instanceId);
        assertEquals("SUCCESS", after.status());
        assertEquals("output", after.currentState());
        assertEquals("草稿v1", resumed.get("draft"));
        assertNull(resumed.get(StateMachineOrchestrator.SUSPENDED_KEY));
    }

    @Test
    void rejectResumesBackToGenerateThenSuspendsAgain() {
        GenExecutor exec = new GenExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(approvalFlow());
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, flows, store);

        engine.run(approvalAgent(), Map.of(), "u1", "c1");
        String instanceId = only(store);

        // signal reject → 回跳 generate 重新生成 → review 再次挂起
        engine.signal(instanceId, "reject", Map.of());
        InMemoryAgentInstanceStore.Instance inst = store.peek(instanceId);
        assertEquals("SUSPENDED", inst.status());
        assertEquals("review", inst.currentState());
        // generate 又跑了一次（草稿v2）
        assertEquals(2, exec.gens);
        assertEquals("草稿v2", inst.contextSnapshot().get("draft"));
    }

    @Test
    void suspendOnErrorFailurePath() {
        GenExecutor exec = new GenExecutor();
        exec.reviewFails = true;
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository();
        // review 改为 onError=SUSPEND：失败即挂起等 signal
        FlowDefinition def = approvalFlow();
        def.getNodes().stream().filter(n -> "review".equals(n.getNodeCode())).forEach(n ->
                n.getNodeConfig().put("stateConfig", Map.of(
                        "onError", "SUSPEND",
                        "awaitingEvents", List.of("retry"))));
        flows.register(def);
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, flows, store);

        engine.run(approvalAgent(), Map.of(), "u1", "c1");
        InMemoryAgentInstanceStore.Instance inst = store.peek(only(store));

        // review 执行失败 → onError=SUSPEND 挂起，未置 FAILED，且未落 review 的 SUCCESS 轨迹
        assertEquals("SUSPENDED", inst.status());
        assertEquals("review", inst.currentState());
        assertTrue(inst.awaitingEvents().contains("RETRY"));
        assertFalse(inst.transitions().stream()
                .anyMatch(t -> "SUCCESS".equals(t.outcome()) && "review".equals(t.toState())));
    }

    @Test
    void signalWithUnexpectedEventIsRejected() {
        GenExecutor exec = new GenExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(approvalFlow());
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(exec, flows, store);

        engine.run(approvalAgent(), Map.of(), "u1", "c1");
        String instanceId = only(store);

        // 事件不在 awaiting_events（approve/reject）中 → 拒绝，保留挂起态
        assertThrows(OrchestrationException.class, () -> engine.signal(instanceId, "delete", Map.of()));
        assertEquals("SUSPENDED", store.peek(instanceId).status());
    }

    private String only(InMemoryAgentInstanceStore store) {
        List<String> ids = store.instanceCodes();
        assertEquals(1, ids.size());
        return ids.get(0);
    }
}
