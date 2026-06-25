package com.nebula.common.ai.orchestration;

import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import com.nebula.common.ai.memory.AgentMemory;
import com.nebula.common.ai.memory.AgentMemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationAgentTest {

    /**
     * 捕获 remember 入参的假记忆门面
     */
    private static class CapturingMemory implements AgentMemory {
        private MemoryRecord captured;

        @Override
        public String agentCode() {
            return "test-orch";
        }

        @Override
        public AgentMemoryConfig config() {
            return new AgentMemoryConfig("test-orch");
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
            this.captured = record;
            return "1";
        }

        @Override
        public List<MemoryRecord> recall(MemoryQuery query) {
            return List.of();
        }

        @Override
        public void forget(String userId, String id) {
        }
    }

    @Test
    void runExecutesGraphAndRemembersTrace() {
        List<String> executed = new java.util.ArrayList<>();
        OrchestrationGraph graph = OrchestrationGraph.builder("test-orch")
                .node("A", ctx -> executed.add("A"))
                .node("B", ctx -> executed.add("B"))
                .edge("A", "B")
                .build();
        CapturingMemory memory = new CapturingMemory();
        OrchestrationAgent agent = new OrchestrationAgent("test-orch", memory, new DagOrchestrator(), graph);

        OrchestrationContext ctx = new OrchestrationContext("u1", "conv-1");
        agent.run(ctx);

        assertEquals(List.of("A", "B"), executed);
        assertNotNull(memory.captured);
        assertEquals(MemoryType.EPISODIC, memory.captured.getType());
        assertEquals("u1", memory.captured.getUserId());
        assertEquals("conv-1", memory.captured.getConversationId());
        assertTrue(memory.captured.getContent().contains("test-orch"));
        assertTrue(memory.captured.getContent().contains("A -> B"));
    }

    @Test
    void runWithoutMemoryStillExecutes() {
        List<String> executed = new java.util.ArrayList<>();
        OrchestrationGraph graph = OrchestrationGraph.builder("no-mem")
                .node("A", ctx -> executed.add("A"))
                .build();
        OrchestrationAgent agent = new OrchestrationAgent("no-mem", null, new DagOrchestrator(), graph);

        agent.run(new OrchestrationContext());

        assertEquals(List.of("A"), executed);
        assertNull(agent.memory());
    }
}
