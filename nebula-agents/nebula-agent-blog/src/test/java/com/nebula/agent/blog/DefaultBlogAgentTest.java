package com.nebula.agent.blog;

import com.nebula.agent.blog.domain.BlogSeries;
import com.nebula.agent.blog.domain.BlogSeriesRequest;
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
     * 捕获最近一次chat请求的桩，仅用于验证调用链
     */
    static class CapturingAiService implements AiService {
        AiRequest lastRequest;

        @Override
        public Map<String, Object> chat(AiRequest request) {
            this.lastRequest = request;
            return Map.of("content", "模拟响应正文");
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
}
