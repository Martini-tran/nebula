package com.nebula.common.ai.memory;

import com.nebula.common.ai.util.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBudgetMemoryWindowTest {

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /**
     * 每条消息（content为4个ASCII字符）估算为 overhead(4) + ceil(4/4)=1 = 5 token。
     */
    private static List<Map<String, Object>> sample() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", "aaaa"));
        messages.add(message("user", "aaaa"));     // u1
        messages.add(message("assistant", "aaaa")); // a1
        messages.add(message("user", "aaaa"));     // u2
        messages.add(message("assistant", "aaaa")); // a2
        return messages;
    }

    @Test
    void eachMessageCostsFiveTokens() {
        assertEquals(5, TokenEstimator.estimateMessage(message("user", "aaaa")));
    }

    @Test
    void keepsSystemAndMostRecentWithinBudget() {
        // 总25 token，预算14：system(5) + 最新1条(5)=10 可放，再加一条=15>14 停止
        List<Map<String, Object>> result = new TokenBudgetMemoryWindow(14).apply(sample());

        assertEquals(2, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("assistant", result.get(1).get("role"));
        assertTrue(TokenEstimator.estimateMessages(result) <= 14);
    }

    @Test
    void keptNonSystemAreInChronologicalOrder() {
        // 预算19：system(5)+5+5=15 可放两条，+5=20>19 停止，保留 u2、a2 且正序
        List<Map<String, Object>> result = new TokenBudgetMemoryWindow(19).apply(sample());

        assertEquals(3, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("user", result.get(1).get("role"));
        assertEquals("assistant", result.get(2).get("role"));
    }

    @Test
    void systemAlwaysKeptEvenWhenOverBudget() {
        // 预算3 小于 system 自身5，非system一条都放不下，但system仍保留
        List<Map<String, Object>> result = new TokenBudgetMemoryWindow(3).apply(sample());

        assertEquals(1, result.size());
        assertEquals("system", result.get(0).get("role"));
    }

    @Test
    void returnsOriginalWhenWithinBudget() {
        List<Map<String, Object>> messages = sample();
        assertSame(messages, new TokenBudgetMemoryWindow(1000).apply(messages));
    }

    @Test
    void nonPositiveBudgetDoesNotTrim() {
        List<Map<String, Object>> messages = sample();
        assertSame(messages, new TokenBudgetMemoryWindow(0).apply(messages));
    }

    @Test
    void emptyAndNullAreSafe() {
        assertNull(new TokenBudgetMemoryWindow(10).apply(null));
        assertTrue(new TokenBudgetMemoryWindow(10).apply(new ArrayList<>()).isEmpty());
    }
}
