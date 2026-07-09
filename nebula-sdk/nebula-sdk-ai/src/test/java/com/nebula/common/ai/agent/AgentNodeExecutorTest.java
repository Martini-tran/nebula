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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 递归子 Agent（{@link AgentNodeExecutor}，阶段 3）单测：验证
 * ① 父 Agent 的 AGENT 节点递归调子 Agent，Input Mapping 传参、子独立建实例（parent_instance_id/parent_node_code
 *    落库）、Output Mapping 写回父 context；
 * ② 深度超 maxAgentDepth 抛异常；
 * ③ A→B→A 循环引用检测抛异常。
 *
 * @author nebula
 */
class AgentNodeExecutorTest {

    /**
     * 子 Agent 的业务执行器：把 city 拼成 travelPlan 写回。
     */
    private static final class ChildExecutor implements FlowNodeExecutor {
        @Override
        public String type() {
            return "PROMPT";
        }

        @Override
        public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
            if ("plan".equals(node.getNodeCode())) {
                Object city = ctx.get("city");
                ctx.put("travelPlan", "去" + city + "的行程");
            }
        }
    }

    /**
     * 子 Agent 流：plan（ENTRY+TERMINAL 单节点跑完）。
     */
    private FlowDefinition childFlow(String flowCode) {
        FlowDefinition def = new FlowDefinition().setFlowCode(flowCode).setEngineType("STATE_MACHINE");
        // 单节点既是入口又是终态：ENTRY 与 TERMINAL 需分开，故 plan(ENTRY) → done(TERMINAL)
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("plan").setStateType("ENTRY").setSortNo(0));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("done").setStateType("TERMINAL").setSortNo(1));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("plan").setToNode("done").setSortNo(0));
        return def;
    }

    /**
     * 父 Agent 流：callChild（AGENT 节点，refAgentCode=child）→ end。
     */
    private FlowDefinition parentFlow(int maxAgentDepth) {
        FlowDefinition def = new FlowDefinition().setFlowCode("parent-flow")
                .setEngineType("STATE_MACHINE").setMaxAgentDepth(maxAgentDepth);
        FlowNodeDefinition call = new FlowNodeDefinition().setNodeCode("callChild")
                .setNodeType("AGENT").setStateType("ENTRY").setSortNo(0);
        call.getNodeConfig().put("refAgentCode", "child");
        call.getNodeConfig().put("inputMapping", Map.of("city", "userCity"));        // 子入参 city ← 父 userCity
        call.getNodeConfig().put("outputMapping", Map.of("travelPlan", "travelPlan")); // 父 travelPlan ← 子 travelPlan
        def.getNodes().add(call);
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("end").setStateType("TERMINAL").setSortNo(1));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("callChild").setToNode("end").setSortNo(0));
        return def;
    }

    private AgentDefinition def(String agentCode, String flowCode) {
        return new AgentDefinition().setAgentCode(agentCode).setFlowCode(flowCode)
                .setFlowVersion(1).setVersion(1);
    }

    /**
     * 组装带 AGENT 执行器的引擎：Supplier 延迟取 engine 打破循环依赖（AGENT 执行器 ↔ engine）。
     */
    private AgentEngine buildEngine(InMemoryFlowDefinitionRepository flows,
                                    InMemoryAgentDefinitionRepository defs,
                                    InMemoryAgentInstanceStore store,
                                    List<FlowNodeExecutor> bizExecutors) {
        AtomicReference<AgentEngine> ref = new AtomicReference<>();
        AgentNodeExecutor agentExec = new AgentNodeExecutor(ref::get, defs);
        java.util.List<FlowNodeExecutor> all = new java.util.ArrayList<>(bizExecutors);
        all.add(agentExec);
        FlowStateMachineFactory factory = new FlowStateMachineFactory(all, new ConditionCompiler());
        AgentEngine engine = new AgentEngine(flows, factory, new StateMachineOrchestrator(),
                store, null, null, defs);
        ref.set(engine);
        return engine;
    }

    @Test
    void recursiveChildAgentWithInputOutputMapping() {
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository()
                .register(parentFlow(8)).register(childFlow("child-flow"));
        InMemoryAgentDefinitionRepository defs = new InMemoryAgentDefinitionRepository();
        defs.register(def("parent", "parent-flow"));
        defs.register(def("child", "child-flow"));
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = buildEngine(flows, defs, store, List.of(new ChildExecutor()));

        OrchestrationContext ctx = engine.run(def("parent", "parent-flow"),
                Map.of("userCity", "杭州"), "u1", "c1");

        // Output Mapping：父 context 拿到子产出的 travelPlan
        assertEquals("去杭州的行程", ctx.get("travelPlan"));

        // 落库：父实例 + 子实例各一，子实例落 parent 关联
        String childInstanceId = store.instanceCodes().stream()
                .filter(id -> id.startsWith("child-")).findFirst().orElseThrow();
        InMemoryAgentInstanceStore.Instance childInst = store.peek(childInstanceId);
        assertEquals("SUCCESS", childInst.status());
        assertEquals("child", childInst.agentCode);
        assertTrue(childInst.parentInstanceId().startsWith("parent-"));
        assertEquals("callChild", childInst.parentNodeCode());
    }

    @Test
    void exceedingMaxAgentDepthThrows() {
        // maxAgentDepth=0：父 flow 的 AGENT 节点执行前栈深已达上限 → 拒绝
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository()
                .register(parentFlow(0)).register(childFlow("child-flow"));
        InMemoryAgentDefinitionRepository defs = new InMemoryAgentDefinitionRepository();
        defs.register(def("parent", "parent-flow"));
        defs.register(def("child", "child-flow"));
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = buildEngine(flows, defs, store, List.of(new ChildExecutor()));

        OrchestrationException ex = assertThrows(OrchestrationException.class, () ->
                engine.run(def("parent", "parent-flow"), Map.of("userCity", "杭州"), "u1", "c1"));
        assertTrue(ex.getMessage().contains("递归深度") || ex.getMessage().contains("深度"));
    }

    @Test
    void cyclicReferenceThrows() {
        // A→B→A：a-flow 的 AGENT 节点引用 b，b-flow 的 AGENT 节点引用 a，递归回 a 时命中循环引用
        FlowDefinition aFlow = new FlowDefinition().setFlowCode("a-flow")
                .setEngineType("STATE_MACHINE").setMaxAgentDepth(8);
        FlowNodeDefinition aCall = new FlowNodeDefinition().setNodeCode("callB")
                .setNodeType("AGENT").setStateType("ENTRY").setSortNo(0);
        aCall.getNodeConfig().put("refAgentCode", "b");
        aFlow.getNodes().add(aCall);
        aFlow.getNodes().add(new FlowNodeDefinition().setNodeCode("aEnd").setStateType("TERMINAL").setSortNo(1));
        aFlow.getEdges().add(new FlowEdgeDefinition().setFromNode("callB").setToNode("aEnd").setSortNo(0));

        FlowDefinition bFlow = new FlowDefinition().setFlowCode("b-flow")
                .setEngineType("STATE_MACHINE").setMaxAgentDepth(8);
        FlowNodeDefinition bCall = new FlowNodeDefinition().setNodeCode("callA")
                .setNodeType("AGENT").setStateType("ENTRY").setSortNo(0);
        bCall.getNodeConfig().put("refAgentCode", "a");
        bFlow.getNodes().add(bCall);
        bFlow.getNodes().add(new FlowNodeDefinition().setNodeCode("bEnd").setStateType("TERMINAL").setSortNo(1));
        bFlow.getEdges().add(new FlowEdgeDefinition().setFromNode("callA").setToNode("bEnd").setSortNo(0));

        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository()
                .register(aFlow).register(bFlow);
        InMemoryAgentDefinitionRepository defs = new InMemoryAgentDefinitionRepository();
        defs.register(def("a", "a-flow"));
        defs.register(def("b", "b-flow"));
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        // 传 PROMPT 执行器覆盖 aEnd/bEnd 终态节点（无操作），避免编图期缺执行器
        AgentEngine engine = buildEngine(flows, defs, store, List.of(new ChildExecutor()));

        OrchestrationException ex = assertThrows(OrchestrationException.class, () ->
                engine.run(def("a", "a-flow"), Map.of(), "u1", "c1"));
        assertTrue(ex.getMessage().contains("循环引用"), "实际异常: " + ex.getMessage());
    }
}
