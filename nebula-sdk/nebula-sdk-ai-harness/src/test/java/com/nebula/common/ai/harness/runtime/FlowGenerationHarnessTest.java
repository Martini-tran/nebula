package com.nebula.common.ai.harness.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.conversation.HarnessMessage;
import com.nebula.common.ai.harness.conversation.HarnessRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowGenerationHarness} 主循环测试。
 */
class FlowGenerationHarnessTest {

    @Test
    void 通过Spi组装请求并完成工具反馈闭环() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakeAiService aiService = new FakeAiService();
            ToolDefinition tool = copilotTool();
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(List.of(tool)),
                    aiService,
                    new ObjectMapper(),
                    (definition, context) -> context.authenticated(),
                    executor,
                    1000);
            FlowGenerationHarness harness = new FlowGenerationHarness(
                    aiService,
                    scheduler,
                    List.of((request, context) -> "system-from-manager"),
                    List.of((request, context) -> List.of("example-from-manager")),
                    5);
            RecordingSink sink = new RecordingSink();

            harness.run(
                    new HarnessRequest(
                            "创建文章流程",
                            List.of(new HarnessMessage("assistant", "历史回复")),
                            "conversation-1",
                            "model-x",
                            0.2),
                    new HarnessCallContext("42", "conversation-1", Set.of("flow:write"), "req-1"),
                    sink);

            assertEquals(2, aiService.chatCount.get());
            assertTrue(aiService.firstRequestHadInjectedPrompts);
            assertTrue(aiService.secondRequestHadToolFeedback);
            assertEquals(List.of(
                            HarnessEvent.TOOL_STARTED,
                            HarnessEvent.TOOL_COMPLETED,
                            HarnessEvent.TOOL_AUDITED,
                            HarnessEvent.TEXT_DELTA,
                            HarnessEvent.CONVERSATION_COMPLETED),
                    sink.types());
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolDefinition copilotTool() {
        return new ToolDefinition() {
            @Override
            public Set<InvocationScope> invocationScopes() {
                return Set.of(InvocationScope.COPILOT_TOOL);
            }

            @Override
            public String code() {
                return "create_draft";
            }

            @Override
            public Map<String, Object> paramsSchema() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public Object invoke(Map<String, Object> params, ToolContext ctx) {
                return Map.of("ok", true, "draftId", "draft-1", "revision", 1);
            }
        };
    }

    private static final class RecordingSink implements HarnessEventSink {
        private final List<HarnessEvent> events = new ArrayList<>();

        @Override
        public void publish(HarnessEvent event) {
            events.add(event);
        }

        List<String> types() {
            return events.stream().map(HarnessEvent::type).toList();
        }
    }

    private static final class FakeAiService implements AiService {
        private final AtomicInteger chatCount = new AtomicInteger();
        private boolean firstRequestHadInjectedPrompts;
        private boolean secondRequestHadToolFeedback;

        @Override
        public Map<String, Object> chat(AiRequest request) {
            int call = chatCount.incrementAndGet();
            if (call == 1) {
                firstRequestHadInjectedPrompts = request.getMessages().stream()
                        .map(message -> String.valueOf(message.get("content")))
                        .toList()
                        .containsAll(List.of("system-from-manager", "example-from-manager", "历史回复", "创建文章流程"));
                return toolCallResponse();
            }
            secondRequestHadToolFeedback = request.getMessages().stream()
                    .anyMatch(message -> "tool".equals(message.get("role"))
                            && "call-1".equals(message.get("tool_call_id"))
                            && String.valueOf(message.get("content")).contains("draft-1"));
            return Map.of("content", "草稿已创建", "role", "assistant");
        }

        private Map<String, Object> toolCallResponse() {
            Map<String, Object> rawCall = new LinkedHashMap<>();
            rawCall.put("id", "call-1");
            rawCall.put("name", "create_draft");
            rawCall.put("arguments", "{}");

            Map<String, Object> assistantMessage = new LinkedHashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", null);
            assistantMessage.put("tool_calls", List.of(Map.of(
                    "id", "call-1",
                    "type", "function",
                    "function", Map.of("name", "create_draft", "arguments", "{}"))));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content", "");
            response.put("toolCalls", List.of(rawCall));
            response.put("assistantMessage", assistantMessage);
            return response;
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
}
