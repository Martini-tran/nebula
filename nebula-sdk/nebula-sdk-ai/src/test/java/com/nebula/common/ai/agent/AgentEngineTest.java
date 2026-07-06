package com.nebula.common.ai.agent;

import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.flow.InMemoryFlowDefinitionRepository;
import com.nebula.common.ai.memory.AgentMemory;
import com.nebula.common.ai.memory.AgentMemoryConfig;
import com.nebula.common.ai.memory.InMemoryAgentMemoryRegistry;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentEngine} 端到端单测：给内存版 store + 记忆注册表，跑「内容生成自我审查循环」demo，验证
 * ① 实例落库（create + 若干 transition + 终态 snapshot）② Import 把记忆写进 context 且被 Inputs 覆盖
 * ③ Export 按策略写回记忆 ④ Export 失败不翻盘 SUCCESS ⑤ 1 Flow : N Agent 复用。
 *
 * @author nebula
 */
class AgentEngineTest {

    /**
     * 生成器执行器：generate 节点每次执行把 score 递增（模拟"重新生成得到更高分"）。
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

    /**
     * fake 记忆门面：recall 从内存 Map 取，remember 写入并记录导出内容。
     */
    private static final class FakeAgentMemory implements AgentMemory {
        final String agentCode;
        final Map<String, String> stored = new java.util.LinkedHashMap<>();
        final List<MemoryRecord> exported = new ArrayList<>();
        boolean exportThrows = false;

        FakeAgentMemory(String agentCode) {
            this.agentCode = agentCode;
        }

        @Override
        public String agentCode() {
            return agentCode;
        }

        @Override
        public AgentMemoryConfig config() {
            return null;
        }

        @Override
        public void saveMessage(String conversationId, Map<String, Object> message) {
        }

        @Override
        public List<Map<String, Object>> loadHistory(String conversationId) {
            return List.of();
        }

        @Override
        public void clearConversation(String conversationId) {
        }

        @Override
        public String remember(MemoryRecord record) {
            if (exportThrows) {
                throw new IllegalStateException("模拟 Export DB 抖动");
            }
            exported.add(record);
            return "mem-" + exported.size();
        }

        @Override
        public List<MemoryRecord> recall(MemoryQuery query) {
            String val = stored.get(query.getText());
            if (val == null) {
                return List.of();
            }
            return List.of(new MemoryRecord().setContent(val));
        }

        @Override
        public void forget(String userId, String id) {
        }
    }

    private FlowStateMachineFactory factory(GeneratorExecutor executor) {
        return new FlowStateMachineFactory(List.of(executor), new ConditionCompiler());
    }

    private FlowDefinition selfReviewFlow() {
        FlowDefinition def = new FlowDefinition().setFlowCode("self-review").setEngineType("STATE_MACHINE");
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("generate").setStateType("ENTRY").setSortNo(0));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("review").setStateType("NORMAL").setSortNo(1));
        def.getNodes().add(new FlowNodeDefinition().setNodeCode("output").setStateType("TERMINAL").setSortNo(2));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("generate").setToNode("review").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("generate")
                .setConditionExpr("get('score') != null && get('score') < 10").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("review").setToNode("output").setSortNo(1));
        return def;
    }

    private AgentDefinition agent(String agentCode, String memoryConfig) {
        return new AgentDefinition().setAgentCode(agentCode).setFlowCode("self-review")
                .setFlowVersion(1).setVersion(1).setMemoryConfig(memoryConfig);
    }

    private AgentEngine engine(GeneratorExecutor executor, InMemoryFlowDefinitionRepository flows,
                               InMemoryAgentInstanceStore store, InMemoryAgentMemoryRegistry registry) {
        return new AgentEngine(flows, factory(executor), new StateMachineOrchestrator(),
                store, registry, null);
    }

    @Test
    void selfReviewLoopPersistsInstanceAndTransitions() {
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(selfReviewFlow());
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        AgentEngine engine = engine(executor, flows, store, new InMemoryAgentMemoryRegistry());

        OrchestrationContext ctx = engine.run(agent("writer", null), Map.of(), "u1", "c1");

        // score 4(<10回跳)->8(<10回跳)->12(>=10出环)：生成 3 次，出环 SUCCESS
        assertEquals(3, executor.runs.get());
        assertEquals(12, ctx.get("score"));
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));

        // 落库：实例存在、终态 SUCCESS、有转移轨迹、终态刷新了 context_snapshot
        InMemoryAgentInstanceStore.Instance inst = store.peek(findOnlyInstanceId(store));
        assertEquals("SUCCESS", inst.status());
        assertEquals("output", inst.currentState());
        assertFalse(inst.transitions().isEmpty());
        assertEquals(12, inst.contextSnapshot().get("score"));
        // SUCCESS 轨迹里应有 generate 首次成功的 delta（含 score）
        assertTrue(inst.transitions().stream()
                .anyMatch(t -> "SUCCESS".equals(t.outcome()) && "generate".equals(t.toState())));
    }

    @Test
    void importWritesMemoryAndInputsOverride() {
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(selfReviewFlow());
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        InMemoryAgentMemoryRegistry registry = new InMemoryAgentMemoryRegistry();
        FakeAgentMemory memory = new FakeAgentMemory("writer");
        memory.stored.put("userProfile", "记忆里的画像");
        memory.stored.put("topic", "记忆里的主题");
        registry.register(memory);

        AgentEngine engine = engine(executor, flows, store, registry);
        String memCfg = "{\"enabled\":true,\"import\":[\"userProfile\",\"topic\"],\"exportStrategy\":\"Append\"}";

        // Inputs 覆盖同名 topic
        OrchestrationContext ctx = engine.run(agent("writer", memCfg),
                Map.of("topic", "入参的主题"), "u1", "c1");

        assertEquals("记忆里的画像", ctx.get("userProfile")); // Import 生效
        assertEquals("入参的主题", ctx.get("topic"));         // Inputs 覆盖 Import
    }

    @Test
    void exportWritesBackByStrategy() {
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(selfReviewFlow());
        InMemoryAgentMemoryRegistry registry = new InMemoryAgentMemoryRegistry();
        FakeAgentMemory memory = new FakeAgentMemory("writer");
        memory.stored.put("userProfile", "老画像");
        registry.register(memory);

        AgentEngine engine = engine(executor, flows, new InMemoryAgentInstanceStore(), registry);
        String memCfg = "{\"enabled\":true,\"import\":[\"userProfile\"],\"exportStrategy\":\"Replace\"}";

        engine.run(agent("writer", memCfg), Map.of("userProfile", "新画像"), "u1", "c1");

        // Export 把 context 里的 userProfile（被 Inputs 覆盖为"新画像"）写回记忆
        assertEquals(1, memory.exported.size());
        assertEquals("新画像", memory.exported.get(0).getContent());
        assertEquals("REPLACE", memory.exported.get(0).getMetadata().get("exportStrategy"));
    }

    @Test
    void exportFailureDoesNotFlipTerminalToFailed() {
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(selfReviewFlow());
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        InMemoryAgentMemoryRegistry registry = new InMemoryAgentMemoryRegistry();
        FakeAgentMemory memory = new FakeAgentMemory("writer");
        memory.exportThrows = true; // Export 时抛异常
        registry.register(memory);

        AgentEngine engine = engine(executor, flows, store, registry);
        String memCfg = "{\"enabled\":true,\"import\":[\"score\"],\"exportStrategy\":\"Append\"}";

        OrchestrationContext ctx = engine.run(agent("writer", memCfg), Map.of(), "u1", "c1");

        // 业务已成功：终态 SUCCESS 不被 Export 失败翻盘
        assertNull(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));
        assertEquals("SUCCESS", store.peek(findOnlyInstanceId(store)).status());
        assertTrue(memory.exported.isEmpty()); // Export 确实失败了
    }

    @Test
    void oneFlowManyAgentsReuseSameFlowIndependently() {
        GeneratorExecutor executor = new GeneratorExecutor();
        InMemoryFlowDefinitionRepository flows = new InMemoryFlowDefinitionRepository().register(selfReviewFlow());
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        InMemoryAgentMemoryRegistry registry = new InMemoryAgentMemoryRegistry();
        registry.register(new FakeAgentMemory("writer-a"));
        registry.register(new FakeAgentMemory("writer-b"));
        AgentEngine engine = engine(executor, flows, store, registry);

        // 两个不同 Agent 引用同一 flowCode，各带不同记忆配置
        engine.run(agent("writer-a", "{\"enabled\":true,\"import\":[\"x\"]}"), Map.of(), "u1", "c1");
        engine.run(agent("writer-b", "{\"enabled\":false}"), Map.of(), "u2", "c2");

        // 两个实例各自落库、互不干扰
        long writerAInstances = store.instanceCodes().stream().filter(c -> c.startsWith("writer-a-")).count();
        long writerBInstances = store.instanceCodes().stream().filter(c -> c.startsWith("writer-b-")).count();
        assertEquals(1, writerAInstances);
        assertEquals(1, writerBInstances);
    }

    private String findOnlyInstanceId(InMemoryAgentInstanceStore store) {
        List<String> ids = store.instanceCodes();
        assertNotNull(ids);
        assertEquals(1, ids.size(), "期望恰好一个实例");
        return ids.get(0);
    }
}
