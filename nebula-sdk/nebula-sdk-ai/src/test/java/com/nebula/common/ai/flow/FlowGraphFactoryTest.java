package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.OrchestrationGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowGraphFactory} 单测：构图、档案继承、引用与环校验。
 *
 * @author nebula
 */
class FlowGraphFactoryTest {

    private final RecordingNodeExecutor executor = new RecordingNodeExecutor();

    private final FlowGraphFactory factory = new FlowGraphFactory(List.of(executor), new ConditionCompiler());

    private FlowDefinition twoNodeFlow() {
        FlowDefinition def = new FlowDefinition().setFlowCode("f1").setDefaultProfileCode("default-profile");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("a").setPromptTemplate("x").setSortNo(0));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("b").setPromptTemplate("y").setSortNo(1)
                .setProfileCode("own-profile"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("b"));
        return def;
    }

    @Test
    void 构图并执行_节点继承流程默认档案() {
        OrchestrationGraph graph = factory.build(twoNodeFlow());
        assertEquals(List.of("a"), graph.roots());

        graph.node("a").execute(new OrchestrationContext());
        graph.node("b").execute(new OrchestrationContext());
        assertEquals("default-profile", executor.profileSeen.get("a"));
        assertEquals("own-profile", executor.profileSeen.get("b"));
    }

    @Test
    void 未知节点类型抛异常() {
        FlowDefinition def = new FlowDefinition().setFlowCode("f2");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("a").setNodeType("HTTP"));
        OrchestrationException ex = assertThrows(OrchestrationException.class, () -> factory.build(def));
        assertTrue(ex.getMessage().contains("HTTP"));
    }

    @Test
    void 成环抛异常() {
        FlowDefinition def = new FlowDefinition().setFlowCode("f3");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("a").setPromptTemplate("x"));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("b").setPromptTemplate("y"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("a").setToNode("b"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("b").setToNode("a"));
        assertThrows(OrchestrationException.class, () -> factory.build(def));
    }
}
