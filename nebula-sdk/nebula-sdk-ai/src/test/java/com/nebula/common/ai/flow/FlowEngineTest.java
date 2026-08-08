package com.nebula.common.ai.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.RunSnapshot;
import com.nebula.common.ai.orchestration.RunStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void 直接运行定义_隔离仓储缓存持久化且不修改原定义() {
        StubAiService aiService = new StubAiService();
        aiService.responseContent = "ok";
        PromptNodeExecutor executor = new PromptNodeExecutor(
                aiService, new InMemoryModelProfileRepository(), new ObjectMapper());
        FlowGraphFactory factory = new FlowGraphFactory(List.of(executor), new ConditionCompiler());
        FlowDefinition definition = new FlowDefinition()
                .setFlowCode("uncommitted-flow")
                .setDefaultProfileCode("draft-profile");
        definition.getNodes().add(new FlowNodeDefinition()
                .setNodeCode("generate")
                .setPromptTemplate("生成 #{topic}")
                .setOutputKey("answer"));

        FlowEngine engine = new FlowEngine(
                code -> { throw new AssertionError("临时执行不应查询正式流程仓储"); },
                factory,
                new DagOrchestrator(),
                new FailingRunStateStore());

        OrchestrationContext ctx = engine.runDefinition(
                definition, Map.of("topic", "测试"), "u1", "c1");

        assertEquals("ok", ctx.get("answer"));
        assertTrue(ctx.getString(FlowEngine.FLOW_CODE_KEY).startsWith("__draft_run__"));
        assertEquals("uncommitted-flow", definition.getFlowCode());
        assertNull(definition.getNodes().getFirst().getProfileCode());
    }

    private static final class FailingRunStateStore implements RunStateStore {

        @Override
        public String startRun(String flowCode, int version, String userId,
                               String conversationId, Map<String, Object> input) {
            throw new AssertionError("临时执行不应创建续跑实例");
        }

        @Override
        public RunSnapshot load(String runId) {
            throw new AssertionError("临时执行不应读取续跑实例");
        }

        @Override
        public void saveNodeDone(String runId, String nodeCode, int seq,
                                 Map<String, Object> attributesSnapshot) {
            throw new AssertionError("临时执行不应保存节点状态");
        }

        @Override
        public void markFinished(String runId, Map<String, Object> attributesSnapshot) {
            throw new AssertionError("临时执行不应保存终态");
        }

        @Override
        public void markFailed(String runId, String failedNodeCode, String error,
                               Map<String, Object> attributesSnapshot) {
            throw new AssertionError("临时执行不应保存失败状态");
        }
    }
}
