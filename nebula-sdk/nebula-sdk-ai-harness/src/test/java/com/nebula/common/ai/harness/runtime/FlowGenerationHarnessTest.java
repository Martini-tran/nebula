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
import com.nebula.common.ai.harness.conversation.HarnessResumeAction;
import com.nebula.common.ai.harness.conversation.HarnessToolContext;
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
import java.util.concurrent.atomic.AtomicReference;

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
                            HarnessEvent.DRAFT_UPDATED,
                            HarnessEvent.TOOL_AUDITED,
                            HarnessEvent.TEXT_DELTA,
                            HarnessEvent.CONVERSATION_COMPLETED),
                    sink.types());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 用户确认后直接恢复原工具参数且不再次调用模型() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakeAiService aiService = new FakeAiService();
            AtomicReference<Map<String, Object>> receivedArguments = new AtomicReference<>();
            AtomicReference<String> receivedToken = new AtomicReference<>();
            ToolDefinition tool = new ToolDefinition() {
                @Override
                public Set<InvocationScope> invocationScopes() {
                    return Set.of(InvocationScope.COPILOT_TOOL);
                }

                @Override
                public String code() {
                    return "real_run_draft";
                }

                @Override
                public Map<String, Object> paramsSchema() {
                    return Map.of(
                            "type", "object",
                            "required", List.of("draftId", "expectedRevision"),
                            "properties", Map.of(
                                    "draftId", Map.of("type", "string"),
                                    "expectedRevision", Map.of("type", "integer"),
                                    "initialInput", Map.of("type", "object")));
                }

                @Override
                public Object invoke(Map<String, Object> params, ToolContext context) {
                    receivedArguments.set(params);
                    receivedToken.set(context.getString(
                            com.nebula.common.ai.harness.conversation.HarnessToolContext
                                    .CONFIRMATION_TOKEN_ATTRIBUTE));
                    return Map.of(
                            "ok", true,
                            "operationId", "op-1",
                            "action", "REAL_RUN",
                            "draftId", "draft-1",
                            "revision", 3,
                            "status", "RUNNING");
                }
            };
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(List.of(tool)), aiService, new ObjectMapper(),
                    (definition, context) -> context.authenticated(), executor, 1000);
            FlowGenerationHarness harness = new FlowGenerationHarness(
                    aiService, scheduler, List.of(), List.of(), 5);
            RecordingSink sink = new RecordingSink();
            Map<String, Object> arguments = Map.of(
                    "draftId", "draft-1",
                    "expectedRevision", 3,
                    "initialInput", Map.of("question", "原始输入"));

            harness.run(new HarnessRequest(
                            "该提示不得进入模型",
                            List.of(),
                            "conversation-1",
                            null,
                            null,
                            "one-time-token",
                            new HarnessResumeAction("real_run_draft", arguments)),
                    new HarnessCallContext("42", "conversation-1", Set.of(), "req-resume"),
                    sink);

            assertEquals(0, aiService.chatCount.get());
            assertEquals(arguments, receivedArguments.get());
            assertEquals("one-time-token", receivedToken.get());
            assertTrue(sink.types().contains(HarnessEvent.OPERATION_UPDATED));
            assertTrue(sink.types().contains(HarnessEvent.CONVERSATION_COMPLETED));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 请求用户确认后立即停止本轮剩余工具且不再次调用模型() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicInteger realRunCalls = new AtomicInteger();
            AtomicInteger unexpectedCalls = new AtomicInteger();
            AiService aiService = new SingleResponseAiService(List.of(
                    toolCall("call-confirm", "real_run_draft"),
                    toolCall("call-unexpected", "unexpected_tool")));
            ToolDefinition realRun = fixedResultTool("real_run_draft", realRunCalls, Map.of(
                    "ok", false,
                    "code", "CONFIRM_REQUIRED",
                    "confirmationId", "cfm-1",
                    "draftId", "draft-1",
                    "revision", 3));
            ToolDefinition unexpected = fixedResultTool(
                    "unexpected_tool", unexpectedCalls, Map.of("ok", true));
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(List.of(realRun, unexpected)), aiService, new ObjectMapper(),
                    (definition, context) -> context.authenticated(), executor, 1000);
            FlowGenerationHarness harness = new FlowGenerationHarness(
                    aiService, scheduler, List.of(), List.of(), 5);
            RecordingSink sink = new RecordingSink();

            harness.run(new HarnessRequest("真实试跑", List.of(), "conversation-1", null, null),
                    new HarnessCallContext("42", "conversation-1", Set.of(), "req-confirm"), sink);

            assertEquals(1, ((SingleResponseAiService) aiService).chatCount.get());
            assertEquals(1, realRunCalls.get());
            assertEquals(0, unexpectedCalls.get());
            assertTrue(sink.types().contains(HarnessEvent.CONFIRMATION_REQUIRED));
            assertEquals(HarnessEvent.CONVERSATION_COMPLETED,
                    sink.types().get(sink.types().size() - 1));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 单独提交确认令牌时拒绝进入模型和工具() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FakeAiService aiService = new FakeAiService();
            AtomicInteger toolCalls = new AtomicInteger();
            ToolDefinition tool = fixedResultTool("real_run_draft", toolCalls, Map.of("ok", true));
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(List.of(tool)), aiService, new ObjectMapper(),
                    (definition, context) -> context.authenticated(), executor, 1000);
            FlowGenerationHarness harness = new FlowGenerationHarness(
                    aiService, scheduler, List.of(), List.of(), 5);
            RecordingSink sink = new RecordingSink();

            harness.run(new HarnessRequest(
                            "不得执行", List.of(), "conversation-1", null, null,
                            "orphan-token", null),
                    new HarnessCallContext("42", "conversation-1", Set.of(), "req-orphan-token"), sink);

            assertEquals(0, aiService.chatCount.get());
            assertEquals(0, toolCalls.get());
            assertEquals(List.of(HarnessEvent.CONVERSATION_FAILED), sink.types());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 续聊绑定活动草稿并隐藏创建工具() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SingleResponseAiService aiService = new SingleResponseAiService(List.of());
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(List.of(copilotTool())), aiService, new ObjectMapper(),
                    (definition, context) -> context.authenticated(), executor, 1000);
            FlowGenerationHarness harness = new FlowGenerationHarness(
                    aiService, scheduler, List.of(), List.of(), 5);

            harness.run(new HarnessRequest(
                            "再加一个审核节点", List.of(), "conversation-1", null, null,
                            "draft-active", 7L, null, null),
                    new HarnessCallContext("42", "conversation-1", Set.of(), "req-active"),
                    new RecordingSink());

            AiRequest request = aiService.lastRequest.get();
            assertTrue(request.getMessages().stream()
                    .map(message -> String.valueOf(message.get("content")))
                    .anyMatch(content -> content.contains("draftId=draft-active")
                            && content.contains("禁止重新创建草稿")));
            assertTrue(request.getTools().stream().noneMatch(schema -> {
                Object function = schema.get("function");
                return function instanceof Map<?, ?> map && "create_draft".equals(map.get("name"));
            }));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 同一工具循环创建草稿后拒绝再次创建() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicInteger invocations = new AtomicInteger();
            ToolDefinition createDraft = fixedResultTool("create_draft", invocations,
                    Map.of("ok", true, "draftId", "draft-1", "revision", 0));
            SingleResponseAiService aiService = new SingleResponseAiService(List.of());
            HarnessToolScheduler scheduler = new HarnessToolScheduler(
                    new ToolRegistry(List.of(createDraft)), aiService, new ObjectMapper(),
                    (definition, context) -> context.authenticated(), executor, 1000);
            HarnessToolContext toolContext = new HarnessToolContext(
                    new HarnessCallContext("42", "conversation-1", Set.of(), "req-create"));

            HarnessToolResult first = scheduler.execute(
                    new HarnessToolCall("call-1", "create_draft", "{}"),
                    toolContext, new RecordingSink());
            HarnessToolResult second = scheduler.execute(
                    new HarnessToolCall("call-2", "create_draft", "{}"),
                    toolContext, new RecordingSink());

            assertTrue(first.success());
            assertEquals(1, invocations.get());
            assertEquals("draft-1", toolContext.getString(HarnessToolContext.ACTIVE_DRAFT_ID_ATTRIBUTE));
            assertTrue(String.valueOf(second.content()).contains("ACTIVE_DRAFT_EXISTS"));
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, Object> toolCall(String id, String name) {
        return Map.of("id", id, "name", name, "arguments", "{}");
    }

    private ToolDefinition fixedResultTool(String code,
                                           AtomicInteger calls,
                                           Map<String, Object> result) {
        return new ToolDefinition() {
            @Override
            public Set<InvocationScope> invocationScopes() {
                return Set.of(InvocationScope.COPILOT_TOOL);
            }

            @Override
            public String code() {
                return code;
            }

            @Override
            public Map<String, Object> paramsSchema() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public Object invoke(Map<String, Object> params, ToolContext context) {
                calls.incrementAndGet();
                return result;
            }
        };
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

    private static final class SingleResponseAiService implements AiService {
        private final AtomicInteger chatCount = new AtomicInteger();
        private final AtomicReference<AiRequest> lastRequest = new AtomicReference<>();
        private final List<Map<String, Object>> toolCalls;

        private SingleResponseAiService(List<Map<String, Object>> toolCalls) {
            this.toolCalls = toolCalls;
        }

        @Override
        public Map<String, Object> chat(AiRequest request) {
            chatCount.incrementAndGet();
            lastRequest.set(request);
            return Map.of("content", "", "toolCalls", toolCalls);
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
