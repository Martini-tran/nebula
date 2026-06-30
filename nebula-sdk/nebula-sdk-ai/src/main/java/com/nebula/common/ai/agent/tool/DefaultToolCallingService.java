package com.nebula.common.ai.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.properties.AiProperties;
import lombok.extern.slf4j.Slf4j;

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
 * 工具调用闭环默认实现
 * 实现 function-calling 循环并内置完整治理：迭代上限、单工具异常隔离、工具白名单/权限校验、调用审计、执行超时。
 *
 * <p>循环每轮复用 {@link AiService#chat(AiRequest)}（经过滤器链与日志）；工具结果以 {@code role=tool} 消息回灌，
 * 严格遵守 OpenAI「带 tool_calls 的 assistant 消息须先于其 tool 结果消息」的顺序约束。
 *
 * @author nebula
 */
@Slf4j
public class DefaultToolCallingService implements ToolCallingService {

    /**
     * 工具产物回灌时单条 content 的最大字符数，避免超长产物撑爆上下文
     */
    private static final int RESULT_CONTENT_LIMIT = 8000;

    private final AiService aiService;

    private final ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper;

    private final AiProperties.ToolCalling config;

    /**
     * 工具执行专用线程池：为单次工具执行施加整体超时（线程级兜底），与模型调用线程隔离。
     * 守护线程、按需扩容、空闲回收；队列零容量保证任务直接交付线程。
     */
    private final ThreadPoolExecutor toolExecutor;

    public DefaultToolCallingService(AiService aiService, ToolRegistry toolRegistry,
                                     ObjectMapper objectMapper, AiProperties aiProperties) {
        this.aiService = aiService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.config = (aiProperties == null ? new AiProperties() : aiProperties).getToolCalling();
        this.toolExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new ToolThreadFactory());
    }

    @Override
    public Map<String, Object> run(AiRequest request, Collection<String> allowedToolCodes, ToolContext toolContext) {
        if (request == null) {
            throw new IllegalArgumentException("AI请求不能为空");
        }
        // 白名单：仅暴露「已启用 + 在请求允许集合内 + 代码中确实存在」的工具；为空则退化为普通对话
        Set<String> whitelist = resolveWhitelist(allowedToolCodes);
        if (whitelist.isEmpty()) {
            return aiService.chat(request);
        }
        // 确保 messages 可追加（请求初始可能只带 prompt）
        ensureMutableMessages(request);
        request.setTools(buildToolSchemas(whitelist));

        Map<String, Object> response = null;
        int maxIterations = Math.max(1, config.getMaxIterations());
        for (int iter = 0; iter < maxIterations; iter++) {
            response = aiService.chat(request);
            List<Map<String, Object>> toolCalls = extractToolCalls(response);
            if (toolCalls.isEmpty()) {
                // 模型不再请求工具：终止并返回最终响应
                return response;
            }
            // OpenAI 约定：先把带 tool_calls 的 assistant 消息追加，再追加各 tool 结果
            appendAssistantMessage(request, response);
            for (Map<String, Object> call : toolCalls) {
                ToolCall toolCall = toToolCall(call);
                Map<String, Object> toolMessage = executeOne(toolCall, whitelist, toolContext);
                request.getMessages().add(toolMessage);
            }
        }
        // 到达迭代上限仍未终止：返回最后一次响应并标记截断，交由上层决定如何处理
        if (response != null) {
            response.put("truncated", true);
            log.warn("工具调用闭环到达迭代上限({})仍未终止，返回最后一次响应", maxIterations);
        }
        return response;
    }

    /**
     * 解析工具白名单：未启用工具调用直接返回空；否则取「允许集合 ∩ 注册表中存在」的工具编码。
     */
    private Set<String> resolveWhitelist(Collection<String> allowedToolCodes) {
        if (!config.isEnabled() || allowedToolCodes == null || allowedToolCodes.isEmpty() || toolRegistry == null) {
            return Set.of();
        }
        Set<String> whitelist = new LinkedHashSet<>();
        for (String code : allowedToolCodes) {
            if (code != null && !code.isBlank() && toolRegistry.contains(code)) {
                whitelist.add(code);
            } else if (code != null && !code.isBlank()) {
                log.warn("工具调用白名单中的工具未注册，已忽略: {}", code);
            }
        }
        return whitelist;
    }

    /**
     * 把白名单工具转成 OpenAI tools 数组：{@code {type:"function", function:{name, description, parameters}}}。
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
            // OpenAI 要求 parameters 为合法 JSON Schema 对象；无声明时给空 object schema
            function.put("parameters", parameters != null ? parameters : emptyObjectSchema());

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", function);
            tools.add(tool);
        }
        return tools;
    }

    /**
     * 执行单个工具调用，返回回灌给模型的 {@code role=tool} 消息。
     * 内置治理：参数解析容错、白名单二次校验、整体超时、异常隔离（错误均以 tool 结果回灌让模型自愈）、审计落日志。
     */
    private Map<String, Object> executeOne(ToolCall toolCall, Set<String> whitelist, ToolContext toolContext) {
        long start = System.currentTimeMillis();
        String content;
        boolean success = false;
        try {
            // 权限二次校验：模型可能臆造未授权工具名
            if (!whitelist.contains(toolCall.name())) {
                content = "错误：工具[" + toolCall.name() + "]未授权或不存在，请勿调用";
            } else {
                ToolDefinition tool = toolRegistry.find(toolCall.name());
                Map<String, Object> params = parseArguments(toolCall.argumentsJson());
                Object result = invokeWithTimeout(tool, params, toolContext);
                content = stringifyResult(result);
                success = true;
            }
        } catch (TimeoutException e) {
            content = "错误：工具执行超时(" + config.getToolTimeoutMs() + "ms)";
            log.warn("工具[{}]执行超时", toolCall.name());
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            content = "错误：工具执行失败 - " + cause.getMessage();
            log.warn("工具[{}]执行异常: {}", toolCall.name(), cause.getMessage());
        }
        audit(toolCall, success, System.currentTimeMillis() - start);
        return toolMessage(toolCall.id(), toolCall.name(), content);
    }

    /**
     * 在配置的整体超时内执行工具（线程级兜底，超时取消任务）。
     */
    private Object invokeWithTimeout(ToolDefinition tool, Map<String, Object> params, ToolContext toolContext)
            throws Exception {
        int timeoutMs = Math.max(1, config.getToolTimeoutMs());
        Callable<Object> task = () -> tool.invoke(params, toolContext);
        Future<Object> future = toolExecutor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * 解析模型生成的入参 JSON 字符串为 Map；解析失败回退空入参（错误由工具自身或后续校验暴露）。
     */
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("工具入参JSON解析失败，回退空入参: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 工具产物归一化为回灌文本：字符串原样，对象序列化为 JSON，超长截断。
     */
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

    /**
     * 审计单次工具调用：复用 AiService 调用日志链路，落 AiCallLogger。审计失败不影响主流程。
     */
    private void audit(ToolCall toolCall, boolean success, long latencyMs) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("type", "toolCall");
            record.put("toolCode", toolCall.name());
            record.put("toolCallId", toolCall.id());
            record.put("success", success);
            record.put("latencyMs", latencyMs);
            aiService.log(record);
        } catch (Exception e) {
            log.debug("工具调用审计落库失败: {}", e.getMessage());
        }
    }

    /**
     * 从模型响应里取归一化的工具调用列表（由 Provider 解析写入）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> response) {
        Object toolCalls = response == null ? null : response.get("toolCalls");
        if (toolCalls instanceof List<?> list && !list.isEmpty()) {
            return (List<Map<String, Object>>) toolCalls;
        }
        return List.of();
    }

    /**
     * 把 Provider 归一化的单条工具调用 Map 转为强类型 {@link ToolCall}。
     */
    private ToolCall toToolCall(Map<String, Object> call) {
        return new ToolCall(
                str(call.get("id")),
                str(call.get("name")),
                call.get("arguments") == null ? "{}" : str(call.get("arguments")));
    }

    /**
     * 把带 tool_calls 的 assistant 原始消息追加进 messages（Provider 已原样保留为 assistantMessage）。
     */
    @SuppressWarnings("unchecked")
    private void appendAssistantMessage(AiRequest request, Map<String, Object> response) {
        Object assistantMessage = response.get("assistantMessage");
        if (assistantMessage instanceof Map) {
            request.getMessages().add((Map<String, Object>) assistantMessage);
        }
    }

    /**
     * 构造 tool 结果消息：{@code {role:"tool", tool_call_id, name, content}}。
     */
    private Map<String, Object> toolMessage(String toolCallId, String name, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId == null ? "" : toolCallId);
        message.put("name", name);
        message.put("content", content == null ? "" : content);
        return message;
    }

    /**
     * 保证 messages 可变且非空：初始仅带 prompt 时，先落一条 user 消息作为对话起点。
     */
    private void ensureMutableMessages(AiRequest request) {
        List<Map<String, Object>> messages = request.getMessages();
        if (messages == null) {
            messages = new ArrayList<>();
            request.setMessages(messages);
        } else if (!(messages instanceof ArrayList)) {
            messages = new ArrayList<>(messages);
            request.setMessages(messages);
        }
        if (messages.isEmpty() && request.getPrompt() != null && !request.getPrompt().isBlank()) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("role", "user");
            user.put("content", request.getPrompt());
            messages.add(user);
        }
    }

    private Map<String, Object> emptyObjectSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 释放工具执行线程池。由装配方在 Bean 销毁时调用。
     */
    public void shutdown() {
        toolExecutor.shutdown();
    }

    /**
     * 工具执行线程工厂：命名便于排障，守护线程不阻塞 JVM 退出。
     */
    private static final class ToolThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ai-tool-call-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
