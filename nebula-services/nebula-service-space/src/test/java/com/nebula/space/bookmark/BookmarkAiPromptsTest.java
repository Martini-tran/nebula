package com.nebula.space.bookmark;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkAiPromptsTest {

    @Test
    void parseToleratesCodeFenceAndChatter() {
        JsonNode node = BookmarkAiPrompts.parse("好的，结果如下：\n```json\n{\"items\":[{\"i\":1,\"folder\":\"前端\"}]}\n```\n");
        assertNotNull(node);
        assertEquals("前端", node.get("items").get(0).get("folder").asText());
        assertNull(BookmarkAiPrompts.parse("抱歉，我无法完成"));
        assertNull(BookmarkAiPrompts.parse("{不是 json}"));
        assertNull(BookmarkAiPrompts.parse(null));
    }

    @Test
    void textReadsScalarsOnly() {
        JsonNode node = BookmarkAiPrompts.parse("{\"a\":\"x\",\"b\":3,\"c\":null,\"d\":[1]}");
        assertEquals("x", BookmarkAiPrompts.text(node, "a"));
        assertEquals("3", BookmarkAiPrompts.text(node, "b"));
        assertNull(BookmarkAiPrompts.text(node, "c"));
        assertNull(BookmarkAiPrompts.text(node, "d"));
        assertNull(BookmarkAiPrompts.text(node, "missing"));
    }

    @Test
    void suggestPromptOnlyMentionsChosenActions() {
        List<BookmarkAiPrompts.Item> items = List.of(
                new BookmarkAiPrompts.Item("Vite", "https://vite.dev/", "", "未分类", List.of()),
                new BookmarkAiPrompts.Item("Pinia", "https://pinia.vuejs.org/", "状态管理", "前端", List.of("文档")));

        String tagsOnly = BookmarkAiPrompts.suggest(List.of("前端"), List.of("文档"), items, Set.of("tags"));
        assertTrue(tagsOnly.contains("现有标签：文档"));
        assertFalse(tagsOnly.contains("现有目录"));
        assertFalse(tagsOnly.contains("\"folder\""));
        assertTrue(tagsOnly.contains("{\"i\":2,\"title\":\"Pinia\",\"url\":\"https://pinia.vuejs.org/\",\"desc\":\"状态管理\",\"tags\":[\"文档\"]}"));

        String all = BookmarkAiPrompts.suggest(List.of(), List.of(), items, BookmarkAiPrompts.ACTIONS);
        assertTrue(all.contains("（还没有目录）"));
        assertTrue(all.contains("\"folder\":\"未分类\""));
        assertTrue(all.contains("- description："));
    }

    @Test
    void planPromptCarriesHint() {
        String prompt = BookmarkAiPrompts.plan(List.of("F1 | 前端 | 3 条"), " 不超过两层 ");
        assertTrue(prompt.contains("F1 | 前端 | 3 条"));
        assertTrue(prompt.contains("用户的额外要求：不超过两层"));
        assertFalse(BookmarkAiPrompts.plan(List.of("F1 | 前端 | 3 条"), "  ").contains("额外要求"));
    }
}
