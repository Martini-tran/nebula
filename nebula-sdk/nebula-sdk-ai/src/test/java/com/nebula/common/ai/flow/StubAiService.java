package com.nebula.common.ai.flow;

import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 测试用 AiService 桩：记录最近一次请求，返回可配置的 content。
 *
 * @author nebula
 */
class StubAiService implements AiService {

    AiRequest lastRequest;

    String responseContent = "STUB";

    @Override
    public Map<String, Object> chat(AiRequest request) {
        this.lastRequest = request;
        return Map.of("content", responseContent, "role", "assistant");
    }

    @Override
    public CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
        return CompletableFuture.completedFuture(chat(request));
    }

    @Override
    public void stream(AiRequest request, AiCallback callback) {
        callback.onComplete(chat(request));
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
