package com.nebula.manager.ai.copilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.tool.SimpleToolContext;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.core.context.UserContext;
import com.nebula.manager.dto.CopilotStreamRequest;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流程设计助手（Copilot）核心服务：混合流式工具循环。
 *
 * <p>由 Provider 能力不对称决定的控制流（见 docs/对话式流程生成与Agent派生设计.md 第一、二章）：
 * <ul>
 *   <li>「判断模型是否要调工具」必须走同步 {@link AiService#chat}（流式路径不解析 tool_calls）；</li>
 *   <li>确定不再调工具的「终止轮」把最终文本按小片推给前端（B1，模拟打字机）。</li>
 * </ul>
 *
 * <p>本服务不复用 {@code DefaultToolCallingService}（其受全局 {@code nebula.ai.toolCalling.enabled}
 * 制约且不吐进度事件），而是照搬其治理逻辑（白名单二次校验/超时/异常隔离/审计）并改造为吐 SSE 事件、
 * 自行决定下发哪些工具。工具集写死，不看全局开关。会话无状态：多轮上下文由前端携带。
 *
 * @author nebula
 */
@Slf4j
@Service
public class FlowCopilotService {

    /**
     * 工具产物回灌单条 content 的最大字符数，避免超长产物撑爆上下文。
     */
    private static final int RESULT_CONTENT_LIMIT = 8000;

    /**
     * 终止轮文本分片窗口（字符数），用于模拟打字机效果。
     */
    private static final int DELTA_CHUNK_SIZE = 24;

    /**
     * 本 Copilot 固定允许的工具编码集合。不读请求参数、不看全局工具调用开关。
     */
    private static final Set<String> COPILOT_TOOL_CODES = new LinkedHashSet<>(List.of(
            "list_node_types", "list_tools", "list_model_profiles", "generate_flow", "derive_agent"));

    /**
     * 携带落库产物的工具编码 → SSE 事件名映射；工具返回 {@code ok:true} 时据此推送 flow/agent 事件。
     */
    private static final Map<String, String> PRODUCT_EVENTS = Map.of(
            "generate_flow", "flow",
            "derive_agent", "agent");

    private final AiService aiService;

    private final ToolRegistry toolRegistry;

    private final AiProperties.ToolCalling toolConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 工具执行专用线程池：为单次工具执行施加整体超时（线程级兜底）。守护线程、按需扩容、零容量队列。
     */
    private final ThreadPoolExecutor toolExecutor;

    /**
     * 终止轮是否走真流式（B2）。默认 false=B1（分片推送已聚合文本，省一次模型调用）。
     */
    private final boolean streamFinalTurn = false;

    public FlowCopilotService(AiService aiService, ToolRegistry toolRegistry, AiProperties aiProperties) {
        this.aiService = aiService;
        this.toolRegistry = toolRegistry;
        this.toolConfig = (aiProperties == null ? new AiProperties() : aiProperties).getToolCalling();
        this.toolExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new CopilotToolThreadFactory());
    }

    /**
     * 运行一次流程设计助手对话（在调用方提供的独立线程内串行执行，逐步推送 SSE 事件）。
     *
     * @param request 请求（本轮 prompt + 历史 messages）
     * @param userId  归属用户ID（由 controller 在请求线程取出后传入，规避 ThreadLocal 跨线程丢失）
     * @param sink    SSE 事件出口
     */
    public void run(CopilotStreamRequest request, Long userId, CopilotSseSink sink) {
        // R1：SSE 独立线程内回填 UserContext，保证工具编程式调用 save/create 时能取到用户身份
        boolean userContextSet = false;
        if (userId != null) {
            UserContext.set(userId);
            userContextSet = true;
        }
        try {
            AiRequest aiRequest = buildAiRequest(request);
            Set<String> whitelist = resolveWhitelist(COPILOT_TOOL_CODES);
            aiRequest.setTools(buildToolSchemas(whitelist));
            aiRequest.setToolChoice("auto");

            ToolContext toolContext = new SimpleToolContext(
                    userId == null ? null : String.valueOf(userId), request.getConversationId());

            loop(aiRequest, whitelist, toolContext, sink);
        } catch (Throwable e) {
            // 放宽到 Throwable：避免 Error/非 RuntimeException 逸出后被 SSE 线程池静默吞掉，
            // 导致响应体为空、前端 events=[] 却拿不到任何 error 事件。
            log.error("Copilot 工具循环执行失败", e);
            sink.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (userContextSet) {
                UserContext.clear();
            }
        }
    }

    /**
     * 混合流式工具循环主体。
     */
    private void loop(AiRequest request, Set<String> whitelist, ToolContext ctx, CopilotSseSink sink) {
        int maxIterations = Math.max(1, toolConfig.getMaxIterations());
        Map<String, Object> response = null;

        for (int iter = 0; iter < maxIterations && !sink.isTerminated(); iter++) {
            response = aiService.chat(request);
            List<Map<String, Object>> toolCalls = extractToolCalls(response);

            if (toolCalls.isEmpty()) {
                // 终止轮：把最终自然语言回复推给前端
                emitFinalTurn(request, response, sink);
                sink.done(response);
                return;
            }

            // OpenAI 约定：先追加带 tool_calls 的 assistant 消息，再追加各 tool 结果
            appendAssistantMessage(request, response);
            for (Map<String, Object> call : toolCalls) {
                ToolCall toolCall = toToolCall(call);
                sink.toolCall(startEvent(toolCall));
                Map<String, Object> toolMessage = executeOne(toolCall, whitelist, ctx, sink);
                request.getMessages().add(toolMessage);
            }
        }

        // 到达迭代上限仍未终止
        if (response != null) {
            emitFinalTurn(request, response, sink);
            sink.done(response);
        } else {
            sink.error("达到迭代上限仍未得到结果");
        }
    }

    /**
     * 推送终止轮文本。B1（默认）：把已聚合的 content 按窗口分片模拟打字机；B2：对同一 messages 再走真流式。
     */
    private void emitFinalTurn(AiRequest request, Map<String, Object> response, CopilotSseSink sink) {
        if (streamFinalTurn) {
            streamFinalTurnB2(request, sink);
            return;
        }
        String content = response == null ? null : str(response.get("content"));
        if (content == null || content.isEmpty()) {
            return;
        }
        for (int i = 0; i < content.length() && !sink.isTerminated(); i += DELTA_CHUNK_SIZE) {
            int end = Math.min(i + DELTA_CHUNK_SIZE, content.length());
            sink.delta(content.substring(i, end));
        }
    }

    /**
     * B2 真流式终止轮：强制不再调工具，逐字推送。
     */
    private void streamFinalTurnB2(AiRequest request, CopilotSseSink sink) {
        request.setTools(new ArrayList<>());
        request.setToolChoice("none");
        aiService.stream(request, new com.nebula.common.ai.api.AiCallback() {
            @Override
            public void onDelta(String content, Map<String, Object> chunk) {
                sink.delta(content);
            }
        });
    }

    /**
     * 执行单个工具调用，返回回灌给模型的 tool 结果消息；成功且携带落库产物时推送 flow/agent 事件。
     * 治理照搬 DefaultToolCallingService：白名单二次校验、整体超时、异常隔离、审计。
     */
    private Map<String, Object> executeOne(ToolCall toolCall, Set<String> whitelist,
                                           ToolContext ctx, CopilotSseSink sink) {
        long start = System.currentTimeMillis();
        String content;
        boolean success = false;
        Object result = null;
        try {
            if (!whitelist.contains(toolCall.name())) {
                content = "错误：工具[" + toolCall.name() + "]未授权或不存在，请勿调用";
            } else {
                ToolDefinition tool = toolRegistry.find(toolCall.name());
                Map<String, Object> params = parseArguments(toolCall.argumentsJson());
                result = invokeWithTimeout(tool, params, ctx);
                content = stringifyResult(result);
                success = true;
            }
        } catch (TimeoutException e) {
            content = "错误：工具执行超时(" + toolConfig.getToolTimeoutMs() + "ms)";
            log.warn("Copilot 工具[{}]执行超时", toolCall.name());
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            content = "错误：工具执行失败 - " + cause.getMessage();
            log.warn("Copilot 工具[{}]执行异常: {}", toolCall.name(), cause.getMessage());
        }
        audit(toolCall, success, System.currentTimeMillis() - start);
        sink.toolCall(doneEvent(toolCall, success, content));
        maybeEmitProduct(toolCall, result, sink);
        return toolMessage(toolCall.id(), toolCall.name(), content);
    }

    /**
     * 工具产物若为落库成功结果（ok=true），按工具类型推送 flow/agent 事件。
     */
    @SuppressWarnings("unchecked")
    private void maybeEmitProduct(ToolCall toolCall, Object result, CopilotSseSink sink) {
        String event = PRODUCT_EVENTS.get(toolCall.name());
        if (event == null || !(result instanceof Map<?, ?> map)) {
            return;
        }
        if (!Boolean.TRUE.equals(map.get("ok"))) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) map);
        payload.remove("ok");
        if ("flow".equals(event)) {
            sink.flow(payload);
        } else if ("agent".equals(event)) {
            sink.agent(payload);
        }
    }

    /**
     * 组装 SDK 请求：注入系统提示词 + 历史消息 + 本轮 prompt。
     */
    private AiRequest buildAiRequest(CopilotStreamRequest request) {
        AiRequest aiRequest = new AiRequest();
        aiRequest.setModel(request.getModel());
        aiRequest.setTemperature(request.getTemperature());
        aiRequest.setConversationId(request.getConversationId());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", CopilotSystemPrompt.TEXT));
        if (request.getMessages() != null) {
            request.getMessages().stream()
                    .filter(m -> m != null && m.getContent() != null && !m.getContent().isBlank())
                    .forEach(m -> messages.add(message(m.getRole() == null ? "user" : m.getRole(), m.getContent())));
        }
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            messages.add(message("user", request.getPrompt()));
        }
        aiRequest.setMessages(messages);
        return aiRequest;
    }

    /**
     * 解析工具白名单：取「配置集合 ∩ 注册表中存在」。不看全局 toolCalling.enabled（Copilot 自主启用）。
     */
    private Set<String> resolveWhitelist(Collection<String> codes) {
        Set<String> whitelist = new LinkedHashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank() && toolRegistry.contains(code)) {
                whitelist.add(code);
            } else if (code != null) {
                log.warn("Copilot 工具未注册，已忽略: {}", code);
            }
        }
        return whitelist;
    }

    /**
     * 把白名单工具转成 OpenAI tools 数组。
     */
    private List<Map<String, Object>> buildToolSchemas(Set<String> whitelist) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (String code : whitelist) {
            ToolDefinition def = toolRegistry.find(code);
            if (def == null) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", def.code());
            if (def.description() != null && !def.description().isBlank()) {
                function.put("description", def.description());
            }
            Map<String, Object> parameters = def.paramsSchema();
            function.put("parameters", parameters != null ? parameters : emptyObjectSchema());

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", function);
            tools.add(tool);
        }
        return tools;
    }

    private Object invokeWithTimeout(ToolDefinition tool, Map<String, Object> params, ToolContext ctx)
            throws Exception {
        int timeoutMs = Math.max(1, toolConfig.getToolTimeoutMs());
        Callable<Object> task = () -> tool.invoke(params, ctx);
        Future<Object> future = toolExecutor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("Copilot 工具入参JSON解析失败，回退空入参: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String stringifyResult(Object result) {
        if (result == null) {
            return "";
        }
        String text;
        if (result instanceof String s) {
            text = s;
        } else {
            try {
                text = objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                text = String.valueOf(result);
            }
        }
        return text.length() > RESULT_CONTENT_LIMIT ? text.substring(0, RESULT_CONTENT_LIMIT) + "...(截断)" : text;
    }

    private void audit(ToolCall toolCall, boolean success, long latencyMs) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("type", "copilotToolCall");
            record.put("toolCode", toolCall.name());
            record.put("toolCallId", toolCall.id());
            record.put("success", success);
            record.put("latencyMs", latencyMs);
            aiService.log(record);
        } catch (Exception e) {
            log.debug("Copilot 工具调用审计落库失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> response) {
        Object toolCalls = response == null ? null : response.get("toolCalls");
        if (toolCalls instanceof List<?> list && !list.isEmpty()) {
            return (List<Map<String, Object>>) toolCalls;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private void appendAssistantMessage(AiRequest request, Map<String, Object> response) {
        Object assistantMessage = response.get("assistantMessage");
        if (assistantMessage instanceof Map) {
            request.getMessages().add((Map<String, Object>) assistantMessage);
        }
    }

    private ToolCall toToolCall(Map<String, Object> call) {
        return new ToolCall(
                str(call.get("id")),
                str(call.get("name")),
                call.get("arguments") == null ? "{}" : str(call.get("arguments")));
    }

    private Map<String, Object> startEvent(ToolCall toolCall) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("status", "start");
        event.put("name", toolCall.name());
        event.put("arguments", parseArguments(toolCall.argumentsJson()));
        return event;
    }

    private Map<String, Object> doneEvent(ToolCall toolCall, boolean success, String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("status", "done");
        event.put("name", toolCall.name());
        event.put("success", success);
        event.put("resultBrief", brief(content));
        return event;
    }

    private Map<String, Object> toolMessage(String toolCallId, String name, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId == null ? "" : toolCallId);
        message.put("name", name);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private Map<String, Object> emptyObjectSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    private static String brief(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 120 ? content.substring(0, 120) + "..." : content;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 释放工具执行线程池（Bean 销毁时调用）。
     */
    @PreDestroy
    public void shutdown() {
        toolExecutor.shutdown();
    }

    /**
     * 归一化的单条工具调用。
     *
     * @param id           工具调用ID（回灌 tool 结果时作 tool_call_id）
     * @param name         目标工具编码
     * @param argumentsJson 入参 JSON 字符串
     */
    private record ToolCall(String id, String name, String argumentsJson) {
    }

    /**
     * 工具执行线程工厂。
     */
    private static final class CopilotToolThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "copilot-tool-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
