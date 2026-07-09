package com.nebula.common.ai.agent;

import com.nebula.common.ai.memory.AgentMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAgentRegistryTest {

    private static Agent agent(String code) {
        return new AbstractAgent(code, null, null) {
        };
    }

    @Test
    void registerAndGetByAgentCode() {
        AgentRegistry registry = new InMemoryAgentRegistry();
        Agent a = agent("blog");
        Agent b = agent("code-helper");
        registry.register(a);
        registry.register(b);

        assertSame(a, registry.get("blog"));
        assertSame(b, registry.get("code-helper"));
        assertTrue(registry.contains("blog"));
        assertEquals(2, registry.all().size());
    }

    @Test
    void getUnknownThrows() {
        AgentRegistry registry = new InMemoryAgentRegistry();
        assertFalse(registry.contains("nope"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("nope"));
    }
}
