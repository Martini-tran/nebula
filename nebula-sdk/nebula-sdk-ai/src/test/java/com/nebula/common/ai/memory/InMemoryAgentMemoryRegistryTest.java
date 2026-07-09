package com.nebula.common.ai.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAgentMemoryRegistryTest {

    private static AgentMemory agent(String code) {
        return new DefaultAgentMemory(new AgentMemoryConfig(code), null, null);
    }

    @Test
    void registerAndGetByAgentCode() {
        AgentMemoryRegistry registry = new InMemoryAgentMemoryRegistry();
        AgentMemory a = agent("code-helper");
        AgentMemory b = agent("customer-service");
        registry.register(a);
        registry.register(b);

        assertSame(a, registry.get("code-helper"));
        assertSame(b, registry.get("customer-service"));
        assertTrue(registry.contains("code-helper"));
        assertEquals(2, registry.all().size());
    }

    @Test
    void getUnknownThrows() {
        AgentMemoryRegistry registry = new InMemoryAgentMemoryRegistry();
        assertFalse(registry.contains("nope"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("nope"));
    }
}
