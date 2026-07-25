package com.nebula.common.ai.rag.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TextChunker} 切块逻辑测试。
 *
 * @author nebula
 */
class TextChunkerTest {

    @Test
    void 空文本返回空列表() {
        assertTrue(TextChunker.chunk(null, 10, 2).isEmpty());
        assertTrue(TextChunker.chunk("", 10, 2).isEmpty());
    }

    @Test
    void 短于窗口的文本切成一块() {
        List<String> chunks = TextChunker.chunk("hello", 10, 2);
        assertEquals(1, chunks.size());
        assertEquals("hello", chunks.get(0));
    }

    @Test
    void 按窗口与重叠切块覆盖全文() {
        // 26 字母，窗口 10 重叠 2 → 步长 8：[0,10) [8,18) [16,26)
        String text = "abcdefghijklmnopqrstuvwxyz";
        List<String> chunks = TextChunker.chunk(text, 10, 2);
        assertEquals(3, chunks.size());
        assertEquals("abcdefghij", chunks.get(0));
        assertEquals("ijklmnopqr", chunks.get(1));
        assertEquals("qrstuvwxyz", chunks.get(2));
    }

    @Test
    void 重叠体现在相邻块首尾重合() {
        String text = "0123456789ABCDE";
        List<String> chunks = TextChunker.chunk(text, 6, 2);
        // 步长 4：[0,6) [4,10) [8,14) [12,15)
        assertEquals("012345", chunks.get(0));
        assertTrue(chunks.get(1).startsWith("4567"));
    }

    @Test
    void 重叠超过窗口时不进入死循环() {
        String text = "abcdefghij";
        List<String> chunks = TextChunker.chunk(text, 4, 10);
        // overlap 被夹到 window-1=3，步长=1，仍能收敛
        assertFalse(chunks.isEmpty());
        assertEquals("abcd", chunks.get(0));
    }

    @Test
    void 零窗口按最小窗口处理不崩溃() {
        List<String> chunks = TextChunker.chunk("abc", 0, 0);
        assertFalse(chunks.isEmpty());
    }
}
