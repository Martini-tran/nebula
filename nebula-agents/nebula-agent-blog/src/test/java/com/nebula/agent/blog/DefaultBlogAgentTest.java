package com.nebula.agent.blog;

import com.nebula.agent.blog.domain.BlogSeries;
import com.nebula.agent.blog.domain.BlogSeriesOutline;
import com.nebula.agent.blog.domain.BlogSeriesRequest;
import com.nebula.agent.blog.domain.BlogTopic;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBlogAgentTest {

    /**
     * 捕获最近一次chat请求的桩，仅用于验证调用链。
     * 返回内容可配置，便于不同用例模拟模型不同输出。
     */
    static class CapturingAiService implements AiService {
        AiRequest lastRequest;
        String responseContent = "模拟响应正文";

        @Override
        public Map<String, Object> chat(AiRequest request) {
            this.lastRequest = request;
            return Map.of("content", responseContent);
        }

        @Override
        public CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
            return CompletableFuture.completedFuture(chat(request));
        }

        @Override
        public void stream(AiRequest request, AiCallback callback) {
        }

        @Override
        public void saveMessage(String conversationId, Map<String, Object> message) {
        }

        @Override
        public List<Map<String, Object>> listMessages(String conversationId) {
            return List.of();
        }

        @Override
        public void log(Map<String, Object> record) {
        }

        @Override
        public void clearConversation(String conversationId) {
        }
    }

    @Test
    void generateSeriesInjectsAgentCodeAndRendersPrompt() {
        CapturingAiService aiService = new CapturingAiService();
        BlogAgent agent = new DefaultBlogAgent(null, aiService);

        BlogSeriesRequest request = new BlogSeriesRequest()
                .setTopic("响应式编程入门")
                .setArticleCount(3)
                .setAudience("Java后端工程师")
                .setStyle("通俗")
                .setLanguage("中文");

        BlogSeries series = agent.generateSeries(request);

        // Agent功能编码自动注入请求
        assertEquals("blog", aiService.lastRequest.getAgentCode());
        // 提示词渲染后包含传入的主题
        assertTrue(aiService.lastRequest.getPrompt().contains("响应式编程入门"));
        // 链路返回非空结果
        assertNotNull(series);
        assertEquals("响应式编程入门", series.getTitle());
        assertEquals(1, series.getArticles().size());
        assertEquals("模拟响应正文", series.getArticles().get(0).getContent());
    }

    @Test
    void generateSeriesTopicsRendersJsonSchemaPrompt() {
        CapturingAiService aiService = new CapturingAiService();
        aiService.responseContent = "{\"title\":\"x\",\"topics\":[]}";
        BlogAgent agent = new DefaultBlogAgent(null, aiService);

        BlogSeriesRequest request = new BlogSeriesRequest()
                .setTopic("响应式编程入门")
                .setArticleCount(3)
                .setAudience("Java后端工程师")
                .setStyle("通俗")
                .setLanguage("中文");

        agent.generateSeriesTopics(request);

        // Agent功能编码自动注入请求
        assertEquals("blog", aiService.lastRequest.getAgentCode());
        String prompt = aiService.lastRequest.getPrompt();
        // 提示词包含主题、篇数，并约束只输出JSON
        assertTrue(prompt.contains("响应式编程入门"));
        assertTrue(prompt.contains("3"));
        assertTrue(prompt.contains("JSON"));
    }

    @Test
    void generateSeriesTopicsParsesOutlineFromJson() {
        CapturingAiService aiService = new CapturingAiService();
        // 模型返回带Markdown代码块包裹的JSON，验证容错抽取
        aiService.responseContent = """
                ```json
                {
                  "title": "响应式编程系列",
                  "summary": "从入门到实战",
                  "topics": [
                    {"order": 1, "title": "为什么需要响应式", "summary": "背景与动机", "category": "理论", "tags": ["Reactor", "背压"]},
                    {"order": 2, "title": "Flux与Mono", "summary": "核心类型", "category": "实践", "tags": ["Flux"]}
                  ]
                }
                ```""";
        BlogAgent agent = new DefaultBlogAgent(null, aiService);

        BlogSeriesOutline outline = agent.generateSeriesTopics(
                new BlogSeriesRequest().setTopic("响应式编程入门").setArticleCount(2));

        assertNotNull(outline);
        assertEquals("响应式编程系列", outline.getTitle());
        assertEquals("从入门到实战", outline.getSummary());
        assertEquals(2, outline.getTopics().size());

        BlogTopic first = outline.getTopics().get(0);
        assertEquals(1, first.getOrder());
        assertEquals("为什么需要响应式", first.getTitle());
        assertEquals("理论", first.getCategory());
        assertEquals(List.of("Reactor", "背压"), first.getTags());
    }

    @Test
    void generateSeriesTopicsFallsBackWhenContentNotJson() {
        CapturingAiService aiService = new CapturingAiService();
        aiService.responseContent = "抱歉，我无法完成。";
        BlogAgent agent = new DefaultBlogAgent(null, aiService);

        BlogSeriesOutline outline = agent.generateSeriesTopics(
                new BlogSeriesRequest().setTopic("响应式编程入门").setArticleCount(2));

        // 解析失败时回退为仅含主题标题的空大纲，链路不中断
        assertNotNull(outline);
        assertEquals("响应式编程入门", outline.getTitle());
        assertTrue(outline.getTopics().isEmpty());
    }
}
