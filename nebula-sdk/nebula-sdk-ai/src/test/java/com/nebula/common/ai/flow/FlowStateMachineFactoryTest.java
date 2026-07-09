package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.statemachine.StateConfig;
import com.nebula.common.ai.orchestration.statemachine.StateMachineGraph;
import com.nebula.common.ai.orchestration.statemachine.StateNode;
import com.nebula.common.ai.orchestration.statemachine.StateType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowStateMachineFactory} 单测：编图 Fail-Fast 校验（ENTRY=1 / TERMINAL≥1）、stateConfig 解析、
 * 成环放行（区别于 DAG 工厂拒绝成环）。
 *
 * @author nebula
 */
class FlowStateMachineFactoryTest {

    private final RecordingNodeExecutor executor = new RecordingNodeExecutor();

    private final FlowStateMachineFactory factory =
            new FlowStateMachineFactory(List.of(executor), new ConditionCompiler());

    private static FlowNodeDefinition node(String code, String stateType) {
        return new FlowNodeDefinition().setNodeCode(code).setStateType(stateType).setPromptTemplate("x");
    }

    @Test
    void 缺少入口态_拒绝建图() {
        FlowDefinition def = new FlowDefinition().setFlowCode("no-entry");
        def.getNodes().add(node("a", "NORMAL"));
        def.getNodes().add(node("end", "TERMINAL"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("end"));

        OrchestrationException ex = assertThrows(OrchestrationException.class, () -> factory.build(def));
        assertTrue(ex.getMessage().contains("入口态"), ex.getMessage());
    }

    @Test
    void 多个入口态_拒绝建图() {
        FlowDefinition def = new FlowDefinition().setFlowCode("dup-entry");
        def.getNodes().add(node("a", "ENTRY"));
        def.getNodes().add(node("b", "ENTRY"));
        def.getNodes().add(node("end", "TERMINAL"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("end"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("b").setToNode("end"));

        OrchestrationException ex = assertThrows(OrchestrationException.class, () -> factory.build(def));
        assertTrue(ex.getMessage().contains("入口态歧义"), ex.getMessage());
    }

    @Test
    void 缺少终态_拒绝建图() {
        FlowDefinition def = new FlowDefinition().setFlowCode("no-terminal");
        def.getNodes().add(node("a", "ENTRY"));
        def.getNodes().add(node("b", "NORMAL"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("b"));

        OrchestrationException ex = assertThrows(OrchestrationException.class, () -> factory.build(def));
        assertTrue(ex.getMessage().contains("终态"), ex.getMessage());
    }

    @Test
    void 成环合法_区别于DAG工厂() {
        // 与 FlowGraphFactoryTest#成环抛异常 对照：同样的环，状态机工厂应放行
        FlowDefinition def = new FlowDefinition().setFlowCode("cyclic");
        def.getNodes().add(node("gen", "ENTRY"));
        def.getNodes().add(node("review", "NORMAL"));
        def.getNodes().add(node("out", "TERMINAL"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("gen").setToNode("review").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("gen")
                .setConditionExpr("get('score') != null && get('score') < 8").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("out").setSortNo(1));

        StateMachineGraph graph = factory.build(def);

        assertEquals("gen", graph.entryState());
        assertEquals(2, graph.outTransitions("review").size());
    }

    @Test
    void stateConfig从nodeConfig解析() {
        FlowNodeDefinition n = node("work", "ENTRY");
        n.getNodeConfig().put("stateConfig", Map.of(
                "retry", Map.of("maxAttempts", 3, "backoffMs", 500, "on", List.of("TIMEOUT", "TOOL_ERROR")),
                "timeoutMs", 30000,
                "onError", "GOTO_STATE",
                "errorState", "human_review"));
        FlowDefinition def = new FlowDefinition().setFlowCode("cfg");
        def.getNodes().add(n);
        def.getNodes().add(node("human_review", "NORMAL"));
        def.getNodes().add(node("end", "TERMINAL"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("work").setToNode("end").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("human_review").setToNode("end").setSortNo(0));

        StateMachineGraph graph = factory.build(def);
        StateNode work = graph.state("work");

        StateConfig cfg = work.config();
        assertEquals(3, cfg.maxAttempts());
        assertEquals(500, cfg.backoffMs());
        assertEquals(30000, cfg.timeoutMs());
        assertEquals(StateConfig.OnError.GOTO_STATE, cfg.onError());
        assertEquals("human_review", cfg.errorState());
        assertTrue(cfg.shouldRetryOn("TIMEOUT"));
        assertTrue(cfg.shouldRetryOn("tool_error")); // 大小写不敏感
    }

    @Test
    void 无stateConfig的节点取默认NONE() {
        FlowDefinition def = new FlowDefinition().setFlowCode("plain");
        def.getNodes().add(node("a", "ENTRY"));
        def.getNodes().add(node("end", "TERMINAL"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("end").setSortNo(0));

        StateMachineGraph graph = factory.build(def);
        StateNode a = graph.state("a");

        assertEquals(StateType.ENTRY, a.type());
        assertEquals(1, a.config().maxAttempts()); // NONE
        assertEquals(StateConfig.OnError.FAIL_INSTANCE, a.config().onError());
    }

    @Test
    void 节点继承流程默认档案() {
        FlowDefinition def = new FlowDefinition().setFlowCode("profile").setDefaultProfileCode("default-p");
        def.getNodes().add(node("a", "ENTRY"));
        def.getNodes().add(node("end", "TERMINAL"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("end").setSortNo(0));

        StateMachineGraph graph = factory.build(def);
        graph.state("a").behavior().execute(new OrchestrationContext());

        assertEquals("default-p", executor.profileSeen.get("a"));
    }
}
