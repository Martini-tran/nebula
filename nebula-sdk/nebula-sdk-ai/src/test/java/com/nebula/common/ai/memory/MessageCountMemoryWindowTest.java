package com.nebula.common.ai.memory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCountMemoryWindowTest {

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    @Test
    void keepsSystemAndRecentNonSystem() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", "你是助手"));
        messages.add(message("user", "u1"));
        messages.add(message("assistant", "a1"));
        messages.add(message("user", "u2"));
        messages.add(message("assistant", "a2"));

        List<Map<String, Object>> result = new MessageCountMemoryWindow(2).apply(messages);

        assertEquals(3, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("u2", result.get(1).get("content"));
        assertEquals("a2", result.get(2).get("content"));
    }

    @Test
    void returnsOriginalWhenWithinLimit() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("user", "u1"));
        messages.add(message("assistant", "a1"));

        assertSame(messages, new MessageCountMemoryWindow(5).apply(messages));
    }

    @Test
    void nonPositiveLimitDoesNotTrim() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("user", "u1"));
        messages.add(message("user", "u2"));

        assertSame(messages, new MessageCountMemoryWindow(0).apply(messages));
    }

    @Test
    void emptyAndNullAreSafe() {
        assertNull(new MessageCountMemoryWindow(3).apply(null));
        assertTrue(new MessageCountMemoryWindow(3).apply(new ArrayList<>()).isEmpty());
    }
}
