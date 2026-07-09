package com.nebula.common.ai.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowEngine} 端到端单测：内存仓储 + 真实执行器 + 条件分支。
 *
 * @author nebula
 */
class FlowEngineTest {

    @Test
    void 条件分支_仅命中分支执行() {
        StubAiService aiService = new StubAiService();
        aiService.responseContent = "series";

        InMemoryModelProfileRepository profiles = new InMemoryModelProfileRepository();
        PromptNodeExecutor executor = new PromptNodeExecutor(aiService, profiles, new ObjectMapper());
        FlowGraphFactory factory = new FlowGraphFactory(List.of(executor), new ConditionCompiler());

        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository();
        FlowDefinition def = new FlowDefinition().setFlowCode("router");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("classify").setPromptTemplate("判断 #{q}")
                .setOutputKey("intent").setSortNo(0));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("series").setPromptTemplate("系列").setSortNo(1));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("single").setPromptTemplate("单篇").setSortNo(2));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("classify").setToNode("series")
                .setConditionExpr("getString('intent') == 'series'"));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("classify").setToNode("single")
                .setConditionExpr("getString('intent') == 'single'"));
        flows.register(def);

        FlowEngine engine = new FlowEngine(flows, factory, new DagOrchestrator());
        OrchestrationContext ctx = engine.run("router", Map.of("q", "写一个系列"), "u1", "c1");

        assertEquals("series", ctx.get("intent"));
        assertTrue(ctx.contains("series"));
        assertFalse(ctx.contains("single"));
        assertEquals("router", ctx.getString(FlowEngine.FLOW_CODE_KEY));
    }
}
