package com.nebula.common.ai.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenEstimatorTest {

    @Test
    void emptyAndNullTextAreZero() {
        assertEquals(0, TokenEstimator.estimateText(null));
        assertEquals(0, TokenEstimator.estimateText(""));
    }

    @Test
    void cjkCountsAboutOneTokenPerChar() {
        // 4个中文字符 ≈ 4 token
        assertEquals(4, TokenEstimator.estimateText("中文测试"));
    }

    @Test
    void asciiCountsAboutOneTokenPerFourChars() {
        // 8个ASCII字符 ≈ 2 token
        assertEquals(2, TokenEstimator.estimateText("abcdefgh"));
    }

    @Test
    void longerTextEstimatesNoLess() {
        int shorter = TokenEstimator.estimateText("hello");
        int longer = TokenEstimator.estimateText("hello world this is longer");
        assertTrue(longer >= shorter);
    }

    @Test
    void messageIncludesOverhead() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", "你好");
        // overhead(4) + 2个中文(2) = 6
        assertEquals(6, TokenEstimator.estimateMessage(message));
    }

    @Test
    void messagesSumChildren() {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> m1 = new LinkedHashMap<>();
        m1.put("role", "user");
        m1.put("content", "你好");
        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("role", "assistant");
        m2.put("content", "你好");
        messages.add(m1);
        messages.add(m2);
        assertEquals(12, TokenEstimator.estimateMessages(messages));
    }
}
