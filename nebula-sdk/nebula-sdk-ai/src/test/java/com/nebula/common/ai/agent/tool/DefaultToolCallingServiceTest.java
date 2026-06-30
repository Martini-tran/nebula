package com.nebula.common.ai.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.properties.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultToolCallingService} 单测：用可编程 fake {@link AiService} 驱动工具循环，
 * 覆盖正常闭环、迭代上限截断、未授权工具拒绝、单工具异常隔离四条路径。
 *
 * @author nebula
 */
class DefaultToolCallingServiceTest {

    /**
     * 正常闭环：第一轮模型请求调 echo 工具，第二轮返回最终答案 → 工具被执行、结果回灌、返回终止响应。
     */
    @Test
    void runExecutesToolThenReturnsFinalResponse() {
        AtomicInteger echoInvoked = new AtomicInteger();
        ToolRegistry registry = registryOf(echoTool(p -> echoInvoked.incrementAndGet()));
        FakeAiService ai = new FakeAiService();
        ai.enqueue(toolCallResponse("call-1", "echo", "{\"text\":\"hi\"}"));
        ai.enqueue(finalResponse("最终答案"));

        DefaultToolCallingService service = service(ai, registry, props(true, 5, 10000));
        AiRequest request = new AiRequest("请回显 hi").setMessages(new ArrayList<>());

        Map<String, Object> result = service.run(request, List.of("echo"), ctx());

        assertEquals(1, echoInvoked.get(), "工具应被执行一次");
        assertEquals("最终答案", result.get("content"));
        assertFalse(result.containsKey("truncated"), "正常终止不应标记截断");
        assertEquals(2, ai.chatCount.get(), "应调用模型两轮");
        // 回灌的消息序列里应同时含 assistant(tool_calls) 与 tool 结果
        boolean hasToolMessage = request.getMessages().stream()
                .anyMatch(m -> "tool".equals(m.get("role")) && "call-1".equals(m.get("tool_call_id")));
        assertTrue(hasToolMessage, "tool 结果应以 tool_call_id 回灌");
    }

    /**
     * 迭代上限：模型每轮都请求工具，到达 maxIterations 仍未终止 → 返回最后一次响应并标记 truncated。
     */
    @Test
    void runMarksTruncatedWhenExceedingMaxIterations() {
        ToolRegistry registry = registryOf(echoTool(p -> {
        }));
        FakeAiService ai = new FakeAiService();
        // 始终返回工具调用，永不终止
        ai.alwaysReturn(toolCallResponse("call-x", "echo", "{\"text\":\"loop\"}"));

        DefaultToolCallingService service = service(ai, registry, props(true, 3, 10000));
        AiRequest request = new AiRequest("死循环").setMessages(new ArrayList<>());

        Map<String, Object> result = service.run(request, List.of("echo"), ctx());

        assertEquals(Boolean.TRUE, result.get("truncated"), "应标记截断");
        assertEquals(3, ai.chatCount.get(), "应恰好迭代 maxIterations 轮");
    }

    /**
     * 权限治理：模型臆造未授权工具名 → 拒绝并回灌错误，循环不崩，最终正常返回。
     */
    @Test
    void runRejectsUnauthorizedTool() {
        ToolRegistry registry = registryOf(echoTool(p -> {
        }));
        FakeAiService ai = new FakeAiService();
        ai.enqueue(toolCallResponse("call-1", "danger", "{}"));
        ai.enqueue(finalResponse("已拒绝危险工具"));

        DefaultToolCallingService service = service(ai, registry, props(true, 5, 10000));
        AiRequest request = new AiRequest("调用未授权工具").setMessages(new ArrayList<>());

        Map<String, Object> result = service.run(request, List.of("echo"), ctx());

        assertEquals("已拒绝危险工具", result.get("content"));
        String toolContent = lastToolContent(request.getMessages());
        assertNotNull(toolContent);
        assertTrue(toolContent.contains("未授权"), "应回灌未授权错误");
    }

    /**
     * 异常隔离：工具执行抛异常 → 错误以 tool 结果回灌，循环继续，最终正常返回。
     */
    @Test
    void runIsolatesToolException() {
        ToolDefinition boom = echoTool(p -> {
            throw new RuntimeException("工具内部炸了");
        });
        ToolRegistry registry = registryOf(boom);
        FakeAiService ai = new FakeAiService();
        ai.enqueue(toolCallResponse("call-1", "echo", "{\"text\":\"x\"}"));
        ai.enqueue(finalResponse("已从工具失败中恢复"));

        DefaultToolCallingService service = service(ai, registry, props(true, 5, 10000));
        AiRequest request = new AiRequest("触发工具异常").setMessages(new ArrayList<>());

        Map<String, Object> result = service.run(request, List.of("echo"), ctx());

        assertEquals("已从工具失败中恢复", result.get("content"));
        String toolContent = lastToolContent(request.getMessages());
        assertNotNull(toolContent);
        assertTrue(toolContent.contains("失败"), "工具异常应以错误文本回灌");
    }

    /**
     * 未启用工具调用：退化为普通 chat，不下发 tools。
     */
    @Test
    void runDegradesToPlainChatWhenDisabled() {
        ToolRegistry registry = registryOf(echoTool(p -> {
        }));
        FakeAiService ai = new FakeAiService();
        ai.enqueue(finalResponse("普通对话"));

        DefaultToolCallingService service = service(ai, registry, props(false, 5, 10000));
        AiRequest request = new AiRequest("普通问题").setMessages(new ArrayList<>());

        Map<String, Object> result = service.run(request, List.of("echo"), ctx());

        assertEquals("普通对话", result.get("content"));
        assertEquals(1, ai.chatCount.get());
        assertTrue(request.getTools() == null || request.getTools().isEmpty(), "未启用时不应下发 tools");
    }

    // ====== 测试辅助 ======

    private DefaultToolCallingService service(AiService ai, ToolRegistry registry, AiProperties props) {
        return new DefaultToolCallingService(ai, registry, new ObjectMapper(), props);
    }

    private AiProperties props(boolean enabled, int maxIterations, int toolTimeoutMs) {
        AiProperties p = new AiProperties();
        p.getToolCalling().setEnabled(enabled);
        p.getToolCalling().setMaxIterations(maxIterations);
        p.getToolCalling().setToolTimeoutMs(toolTimeoutMs);
        return p;
    }

    private ToolContext ctx() {
        return new SimpleToolContext("u1", "c1");
    }

    private ToolRegistry registryOf(ToolDefinition tool) {
        return new ToolRegistry(List.of(tool));
    }

    /**
     * 构造一个编码为 echo 的工具，invoke 时执行给定回调（用于断言调用/注入异常）。
     */
    private ToolDefinition echoTool(java.util.function.Consumer<Map<String, Object>> onInvoke) {
        return new ToolDefinition() {
            @Override
            public String code() {
                return "echo";
            }

            @Override
            public String description() {
                return "回显文本";
            }

            @Override
            public Object invoke(Map<String, Object> params, ToolContext ctx) {
                onInvoke.accept(params);
                return params == null ? "" : params.get("text");
            }
        };
    }

    private Map<String, Object> toolCallResponse(String id, String name, String argumentsJson) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("arguments", argumentsJson);
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", id);
        toolCall.put("name", name);
        toolCall.put("arguments", argumentsJson);

        Map<String, Object> rawCall = new LinkedHashMap<>();
        rawCall.put("id", id);
        rawCall.put("type", "function");
        rawCall.put("function", function);
        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", null);
        assistantMessage.put("tool_calls", List.of(rawCall));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", "");
        response.put("role", "assistant");
        response.put("toolCalls", new ArrayList<>(List.of(toolCall)));
        response.put("assistantMessage", assistantMessage);
        response.put("finishReason", "tool_calls");
        return response;
    }

    private Map<String, Object> finalResponse(String content) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("role", "assistant");
        response.put("finishReason", "stop");
        return response;
    }

    @SuppressWarnings("unchecked")
    private String lastToolContent(List<Map<String, Object>> messages) {
        String last = null;
        for (Map<String, Object> m : messages) {
            if ("tool".equals(m.get("role"))) {
                last = (String) m.get("content");
            }
        }
        return last;
    }

    /**
     * 可编程 fake AiService：按入队顺序返回 chat 响应；alwaysReturn 设定后恒定返回。
     */
    private static final class FakeAiService implements AiService {

        private final Deque<Map<String, Object>> responses = new ArrayDeque<>();

        private Map<String, Object> always;

        private final AtomicInteger chatCount = new AtomicInteger();

        void enqueue(Map<String, Object> response) {
            responses.add(response);
        }

        void alwaysReturn(Map<String, Object> response) {
            this.always = response;
        }

        @Override
        public Map<String, Object> chat(AiRequest request) {
            chatCount.incrementAndGet();
            if (always != null) {
                // 每轮返回新副本，避免 toolCalls 列表被循环消费后影响后续轮
                return deepCopyResponse(always);
            }
            Map<String, Object> r = responses.poll();
            return r == null ? Map.of("content", "", "role", "assistant") : r;
        }

        @Override
        public CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
            return CompletableFuture.completedFuture(chat(request));
        }

        @Override
        public void stream(AiRequest request, AiCallback callback) {
            // 工具循环不走流式，无需实现
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

        @SuppressWarnings("unchecked")
        private Map<String, Object> deepCopyResponse(Map<String, Object> src) {
            Map<String, Object> copy = new LinkedHashMap<>(src);
            Object toolCalls = src.get("toolCalls");
            if (toolCalls instanceof List<?> list) {
                List<Map<String, Object>> copied = new ArrayList<>();
                for (Object o : list) {
                    copied.add(new LinkedHashMap<>((Map<String, Object>) o));
                }
                copy.put("toolCalls", copied);
            }
            return copy;
        }
    }
}
