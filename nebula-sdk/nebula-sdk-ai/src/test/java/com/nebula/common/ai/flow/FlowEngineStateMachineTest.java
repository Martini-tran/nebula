package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowEngine} 状态机分流端到端单测：engine_type=STATE_MACHINE 的流程走状态机内核，
 * 跑通"内容生成自我审查循环" demo（文档第十二章阶段 1 demo）；未装配内核时明确报错。
 *
 * @author nebula
 */
class FlowEngineStateMachineTest {

    /**
     * 测试用生成器执行器：每次执行把 score 递增，模拟"重新生成得到更高分"。
     */
    private static final class GeneratorExecutor implements FlowNodeExecutor {
        final AtomicInteger runs = new AtomicInteger();

        @Override
        public String type() {
            return "PROMPT";
        }

        @Override
        public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
            if ("generate".equals(node.getNodeCode())) {
                ctx.put("score", runs.incrementAndGet() * 4); // 4, 8, 12...
            }
        }
    }

    private FlowEngine engineWith(GeneratorExecutor executor, InMemoryFlowDefinitionRepository flows) {
        FlowGraphFactory dagFactory = new FlowGraphFactory(List.of(executor), new ConditionCompiler());
        FlowStateMachineFactory smFactory = new FlowStateMachineFactory(List.of(executor), new ConditionCompiler());
        StateMachineOrchestrator smOrchestrator = new StateMachineOrchestrator(Executors.newCachedThreadPool());
        return new FlowEngine(flows, dagFactory, new DagOrchestrator(), null, smFactory, smOrchestrator);
    }

    private FlowDefinition selfReviewFlow() {
        FlowDefinition def = new FlowDefinition().setFlowCode("self-review").setEngineType("STATE_MACHINE");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("generate").setStateType("ENTRY").setSortNo(0));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("review").setStateType("NORMAL").setSortNo(1));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("output").setStateType("TERMINAL").setSortNo(2));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("generate").setToNode("review").setSortNo(0));
        // score<10 回跳重生成（成环）；否则 default 出环到终态
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("generate")
                .setConditionExpr("get('score') != null && get('score') < 10").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("output").setSortNo(1));
        return def;
    }

    @Test
    void 状态机分流_自我审查循环出环() {
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository();
        flows.register(selfReviewFlow());
        FlowEngine engine = engineWith(executor, flows);

        OrchestrationContext ctx = engine.run("self-review", Map.of(), "u1", "c1");

        // score: 4(<10回跳) -> 8(<10回跳) -> 12(>=10出环) → 生成 3 次
        assertEquals(3, executor.runs.get());
        assertEquals(12, ctx.get("score"));
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));
        assertEquals("self-review", ctx.getString(FlowEngine.FLOW_CODE_KEY));
    }

    @Test
    void 未装配状态机内核_明确报错() {
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository();
        flows.register(selfReviewFlow());
        // 只给 DAG 内核，不给状态机工厂/内核
        FlowGraphFactory dagFactory = new FlowGraphFactory(List.of(executor), new ConditionCompiler());
        FlowEngine engine = new FlowEngine(flows, dagFactory, new DagOrchestrator());

        OrchestrationException ex = assertThrows(OrchestrationException.class,
                () -> engine.run("self-review", Map.of(), "u1", "c1"));
        assertTrue(ex.getMessage().contains("STATE_MACHINE"), ex.getMessage());
    }

    @Test
    void DAG流程不受状态机分流影响() {
        // 同一引擎，engine_type 缺省(DAG) 的流程仍走 DAG 内核
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository();
        FlowDefinition dag = new FlowDefinition().setFlowCode("plain-dag");
        dag.getNodes().add(new FlowNodeDefinition().setNodeCode("generate").setSortNo(0));
        flows.register(dag);
        FlowEngine engine = engineWith(executor, flows);

        OrchestrationContext ctx = engine.run("plain-dag", Map.of(), "u1", "c1");

        assertEquals(1, executor.runs.get()); // DAG 单次执行，不成环
        assertEquals(4, ctx.get("score"));
    }
}
