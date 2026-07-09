package com.nebula.common.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.statemachine.StateMachineGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * graph_snapshot 自包含 round-trip 单测：把编译源图（FlowDefinition）序列化为 JSON（模拟 graph_snapshot 落库），
 * 再反序列化并用 {@link FlowStateMachineFactory} 重新编回状态机图，验证「续跑/回放只从 snapshot 加载图，
 * 不回查 node/edge 表」——反序列化的图与原图入口态、出边、stateConfig、guard 一致。
 *
 * @author nebula
 */
class AgentInstanceSnapshotReloadTest {

    private static final class NoopExecutor implements FlowNodeExecutor {
        @Override
        public String type() {
            return "PROMPT";
        }

        @Override
        public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    private final FlowStateMachineFactory factory =
            new FlowStateMachineFactory(List.of(new NoopExecutor()), new ConditionCompiler());

    private FlowDefinition sourceFlow() {
        FlowDefinition def = new FlowDefinition().setFlowCode("sr").setEngineType("STATE_MACHINE").setMaxTransitions(50);
        FlowNodeDefinition gen = new FlowNodeDefinition().setNodeCode("gen").setStateType("ENTRY").setSortNo(0);
        // 带 stateConfig 的节点，验证 stateConfig 随 snapshot round-trip
        gen.getNodeConfig().put("stateConfig", Map.of(
                "retry", Map.of("maxAttempts", 3, "on", List.of("TIMEOUT")),
                "timeoutMs", 30000));
        def.getNodes().add(gen);
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("review").setStateType("NORMAL").setSortNo(1));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("out").setStateType("TERMINAL").setSortNo(2));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("gen").setToNode("review").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("gen")
                .setConditionExpr("get('score') != null && get('score') < 8").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("out").setSortNo(1));
        return def;
    }

    @Test
    void snapshotSerializesAndReloadsToEquivalentGraph() throws Exception {
        FlowDefinition source = sourceFlow();

        // 模拟落库：序列化为 graph_snapshot JSON
        String snapshot = mapper.writeValueAsString(source);
        assertNotNull(snapshot);

        // 模拟续跑/回放：从 snapshot 反序列化并重新编图（不回查 node/edge 表）
        FlowDefinition reloaded = mapper.readValue(snapshot, FlowDefinition.class);
        StateMachineGraph graph = factory.build(reloaded);

        // 入口态、成环出边、max_transitions 一致
        assertEquals("gen", graph.entryState());
        assertEquals(50, graph.maxTransitions());
        assertEquals(2, graph.outTransitions("review").size());

        // stateConfig 随 snapshot round-trip 保真
        assertEquals(3, graph.state("gen").config().maxAttempts());
        assertEquals(30000, graph.state("gen").config().timeoutMs());
    }

    @Test
    void reloadedGraphGuardStillEvaluates() throws Exception {
        FlowDefinition reloaded = mapper.readValue(
                mapper.writeValueAsString(sourceFlow()), FlowDefinition.class);
        StateMachineGraph graph = factory.build(reloaded);

        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put("score", 5); // <8：回跳出边应命中
        var backEdge = graph.outTransitions("review").get(0); // sortNo 0 = 回跳
        assertEquals("gen", backEdge.to());
        org.junit.jupiter.api.Assertions.assertTrue(backEdge.isSatisfied(ctx),
                "反序列化后的 guard 仍应可求值命中");
    }
}
