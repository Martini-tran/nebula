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
import com.nebula.common.ai.harness.conversation.HarnessToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HarnessToolScheduler} 的调用域、上下文传播、参数校验与事件契约测试。
 */
class HarnessToolSchedulerTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void 仅下发Copilot域工具且工作线程保持认证上下文() {
        AtomicReference<HarnessCallContext> receivedContext = new AtomicReference<>();
        AtomicReference<String> toolThread = new AtomicReference<>();
        ToolDefinition copilotTool = tool("draft_action", Set.of(InvocationScope.COPILOT_TOOL), context -> {
            receivedContext.set(((HarnessToolContext) context).callContext());
            toolThread.set(Thread.currentThread().getName());
            return Map.of("ok", true, "value", "done");
        });
        ToolDefinition flowOnly = tool("flow_only", Set.of(InvocationScope.FLOW_NODE), context -> "blocked");
        RecordingAiService aiService = new RecordingAiService();
        HarnessToolScheduler scheduler = scheduler(aiService, copilotTool, flowOnly);
        HarnessCallContext callContext = context();
        HarnessToolContext toolContext = new HarnessToolContext(callContext);
        RecordingSink sink = new RecordingSink();

        List<Map<String, Object>> schemas = scheduler.toolSchemas(callContext);
        HarnessToolResult result = scheduler.execute(
                new HarnessToolCall("call-1", "draft_action", "{\"name\":\"article\"}"),
                toolContext,
                sink);

        assertEquals(1, schemas.size());
        assertEquals("draft_action", functionName(schemas.getFirst()));
        assertTrue(result.success());
        assertSame(callContext, receivedContext.get(), "工具线程必须收到创建时的同一认证上下文");
        assertFalse(Thread.currentThread().getName().equals(toolThread.get()), "工具应在隔离线程执行");
        assertEquals(List.of(
                        HarnessEvent.TOOL_STARTED,
                        HarnessEvent.TOOL_COMPLETED,
                        HarnessEvent.TOOL_AUDITED),
                sink.types());
        assertEquals(1, aiService.auditRecords.size());
        assertEquals("req-1", aiService.auditRecords.getFirst().get("requestId"));
    }

    @Test
    void 非法Json和缺少必填字段均不会执行工具() {
        AtomicBoolean invoked = new AtomicBoolean();
        ToolDefinition tool = tool("draft_action", Set.of(InvocationScope.COPILOT_TOOL), context -> {
            invoked.set(true);
            return Map.of("ok", true);
        });
        HarnessToolScheduler scheduler = scheduler(new RecordingAiService(), tool);

        HarnessToolResult malformed = scheduler.execute(
                new HarnessToolCall("call-1", "draft_action", "{bad-json"),
                new HarnessToolContext(context()),
                new RecordingSink());
        HarnessToolResult missingRequired = scheduler.execute(
                new HarnessToolCall("call-2", "draft_action", "{}"),
                new HarnessToolContext(context()),
                new RecordingSink());

        assertFalse(malformed.success());
        assertFalse(missingRequired.success());
        assertFalse(invoked.get());
        assertTrue(malformed.content().contains("INVALID_TOOL_ARGUMENTS"));
        assertTrue(missingRequired.content().contains("$.name"));
    }

    @Test
    void 执行前再次拒绝未授权工具() {
        AtomicBoolean allow = new AtomicBoolean(true);
        ToolDefinition tool = tool("draft_action", Set.of(InvocationScope.COPILOT_TOOL), context -> "never");
        ToolRegistry registry = new ToolRegistry(List.of(tool));
        HarnessToolScheduler scheduler = new HarnessToolScheduler(
                registry,
                new RecordingAiService(),
                new ObjectMapper(),
                (definition, context) -> allow.get(),
                executor,
                1000);
        assertEquals(1, scheduler.toolSchemas(context()).size());
        allow.set(false);

        HarnessToolResult result = scheduler.execute(
                new HarnessToolCall("call-1", "draft_action", "{\"name\":\"article\"}"),
                new HarnessToolContext(context()),
                new RecordingSink());

        assertFalse(result.success());
        assertTrue(result.content().contains("TOOL_NOT_AUTHORIZED"));
    }

    private HarnessToolScheduler scheduler(RecordingAiService aiService, ToolDefinition... tools) {
        return new HarnessToolScheduler(
                new ToolRegistry(List.of(tools)),
                aiService,
                new ObjectMapper(),
                (tool, context) -> context.authenticated(),
                executor,
                1000);
    }

    private ToolDefinition tool(String code,
                                Set<InvocationScope> scopes,
                                java.util.function.Function<ToolContext, Object> invoke) {
        return new ToolDefinition() {
            @Override
            public Set<InvocationScope> invocationScopes() {
                return scopes;
            }

            @Override
            public String code() {
                return code;
            }

            @Override
            public Map<String, Object> paramsSchema() {
                Map<String, Object> name = Map.of("type", "string");
                return Map.of(
                        "type", "object",
                        "required", List.of("name"),
                        "properties", Map.of("name", name));
            }

            @Override
            public Object invoke(Map<String, Object> params, ToolContext context) {
                return invoke.apply(context);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private String functionName(Map<String, Object> schema) {
        return String.valueOf(((Map<String, Object>) schema.get("function")).get("name"));
    }

    private HarnessCallContext context() {
        return new HarnessCallContext("42", "conversation-1", Set.of("flow:write"), "req-1");
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

    private static final class RecordingAiService implements AiService {
        private final List<Map<String, Object>> auditRecords = new ArrayList<>();

        @Override
        public Map<String, Object> chat(AiRequest request) {
            return Map.of();
        }

        @Override
        public CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
            return CompletableFuture.completedFuture(Map.of());
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
            auditRecords.add(new LinkedHashMap<>(record));
        }

        @Override
        public void clearConversation(String conversationId) {
        }
    }
}
