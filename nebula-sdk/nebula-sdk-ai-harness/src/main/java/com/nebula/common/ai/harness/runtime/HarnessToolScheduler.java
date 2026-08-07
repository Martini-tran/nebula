package com.nebula.common.ai.harness.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.conversation.HarnessToolAuthorizer;
import com.nebula.common.ai.harness.conversation.HarnessToolContext;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Harness 工具调度器。
 *
 * <p>统一负责调用域与授权双检、严格 JSON/Schema 校验、超时隔离、结果预算、审计和生命周期事件。
 * schema 下发与真正执行分别检查一次，防止模型伪造工具名或运行期注册表变化造成越权。
 *
 * @author nebula
 */
@Slf4j
public class HarnessToolScheduler {

    private static final int RESULT_CONTENT_LIMIT = 8000;

    private final ToolRegistry toolRegistry;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final HarnessToolAuthorizer authorizer;
    private final ExecutorService toolExecutor;
    private final int toolTimeoutMs;

    public HarnessToolScheduler(ToolRegistry toolRegistry,
                                AiService aiService,
                                ObjectMapper objectMapper,
                                HarnessToolAuthorizer authorizer,
                                ExecutorService toolExecutor,
                                int toolTimeoutMs) {
        this.toolRegistry = toolRegistry;
        this.aiService = aiService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.authorizer = authorizer;
        this.toolExecutor = toolExecutor;
        this.toolTimeoutMs = Math.max(1, toolTimeoutMs);
    }

    /**
     * 构造当前调用者可见的 OpenAI tools schema。
     */
    public List<Map<String, Object>> toolSchemas(HarnessCallContext context) {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (ToolDefinition tool : availableTools(context)) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.code());
            if (tool.description() != null && !tool.description().isBlank()) {
                function.put("description", tool.description());
            }
            function.put("parameters", tool.paramsSchema() == null ? emptyObjectSchema() : tool.paramsSchema());
            schemas.add(Map.of("type", "function", "function", function));
        }
        return schemas;
    }

    /**
     * 执行一条模型工具调用。任何可恢复错误均转为结构化结果回灌模型，不向主循环抛出。
     */
    public HarnessToolResult execute(HarnessToolCall call,
                                     HarnessToolContext toolContext,
                                     HarnessEventSink sink) {
        long startedAt = System.currentTimeMillis();
        Map<String, Object> params = null;
        Object result = null;
        String content;
        boolean success = false;

        publish(sink, HarnessEvent.TOOL_STARTED, startedPayload(call));
        try {
            ToolDefinition tool = resolveAuthorizedTool(call, toolContext.callContext());
            params = parseArguments(call.argumentsJson());
            List<String> validationErrors = HarnessJsonSchemaValidator.validate(params, tool.paramsSchema());
            if (!validationErrors.isEmpty()) {
                result = invalidArguments(validationErrors);
            } else {
                result = invokeWithTimeout(tool, params, toolContext);
                success = resultSucceeded(result);
            }
            content = stringifyResult(result);
        } catch (InvalidToolArgumentsException e) {
            result = invalidArguments(List.of(e.getMessage()));
            content = stringifyResult(result);
        } catch (UnauthorizedToolException e) {
            result = failure("TOOL_NOT_AUTHORIZED", e.getMessage(), "重新读取可用工具列表后再调用");
            content = stringifyResult(result);
            log.warn("Harness 拒绝工具调用: requestId={}, tool={}, reason={}",
                    toolContext.callContext().requestId(), call.name(), e.getMessage());
        } catch (TimeoutException e) {
            result = failure("TOOL_TIMEOUT", "工具执行超时(" + toolTimeoutMs + "ms)", "缩小单次操作后重试");
            content = stringifyResult(result);
            log.warn("Harness 工具执行超时: requestId={}, tool={}, timeoutMs={}",
                    toolContext.callContext().requestId(), call.name(), toolTimeoutMs);
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            result = failure("TOOL_EXECUTION_FAILED", "工具执行失败: " + message, "检查参数或选择其他工具后重试");
            content = stringifyResult(result);
            log.warn("Harness 工具执行异常: requestId={}, tool={}, error={}",
                    toolContext.callContext().requestId(), call.name(), message);
        }

        long latencyMs = System.currentTimeMillis() - startedAt;
        HarnessToolResult execution = new HarnessToolResult(
                call.id(), call.name(), success, result, content, latencyMs);
        publish(sink, HarnessEvent.TOOL_COMPLETED, completedPayload(execution));
        audit(execution, toolContext.callContext(), sink);
        return execution;
    }

    private Collection<ToolDefinition> availableTools(HarnessCallContext context) {
        Set<String> seen = new LinkedHashSet<>();
        List<ToolDefinition> tools = new ArrayList<>();
        for (ToolDefinition tool : toolRegistry.all()) {
            if (isCopilotTool(tool) && isAuthorized(tool, context) && seen.add(tool.code())) {
                tools.add(tool);
            }
        }
        return tools;
    }

    private ToolDefinition resolveAuthorizedTool(HarnessToolCall call, HarnessCallContext context) {
        if (call == null || call.name() == null || call.name().isBlank()) {
            throw new UnauthorizedToolException("工具编码为空");
        }
        ToolDefinition tool = toolRegistry.find(call.name());
        if (tool == null || !isCopilotTool(tool) || !isAuthorized(tool, context)) {
            throw new UnauthorizedToolException("工具[" + call.name() + "]未授权或不存在");
        }
        return tool;
    }

    private boolean isAuthorized(ToolDefinition tool, HarnessCallContext context) {
        return authorizer != null && authorizer.isAuthorized(tool, context);
    }

    private boolean isCopilotTool(ToolDefinition tool) {
        return tool != null && tool.invocationScopes() != null
                && tool.invocationScopes().contains(InvocationScope.COPILOT_TOOL);
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() { });
            if (value == null) {
                throw new InvalidToolArgumentsException("工具参数必须是 JSON 对象");
            }
            return value;
        } catch (InvalidToolArgumentsException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidToolArgumentsException("工具参数不是合法 JSON 对象: " + e.getMessage());
        }
    }

    private Object invokeWithTimeout(ToolDefinition tool,
                                     Map<String, Object> params,
                                     HarnessToolContext context) throws Exception {
        Callable<Object> task = () -> tool.invoke(params, context);
        Future<Object> future = toolExecutor.submit(task);
        try {
            return future.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    private void audit(HarnessToolResult result, HarnessCallContext context, HarnessEventSink sink) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("type", "harnessToolCall");
        audit.put("requestId", context.requestId());
        audit.put("conversationId", context.sessionId());
        audit.put("userId", context.userId());
        audit.put("toolCode", result.toolCode());
        audit.put("toolCallId", result.callId());
        audit.put("success", result.success());
        audit.put("latencyMs", result.latencyMs());
        try {
            aiService.log(audit);
        } catch (RuntimeException e) {
            log.debug("Harness 工具调用审计持久化失败: requestId={}, tool={}, error={}",
                    context.requestId(), result.toolCode(), e.getMessage());
        }
        publish(sink, HarnessEvent.TOOL_AUDITED, audit);
    }

    private Map<String, Object> startedPayload(HarnessToolCall call) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "start");
        payload.put("name", call == null ? null : call.name());
        payload.put("toolCallId", call == null ? null : call.id());
        return payload;
    }

    private Map<String, Object> completedPayload(HarnessToolResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "done");
        payload.put("name", result.toolCode());
        payload.put("toolCallId", result.callId());
        payload.put("success", result.success());
        payload.put("resultBrief", brief(result.content()));
        payload.put("result", result.result());
        payload.put("latencyMs", result.latencyMs());
        return payload;
    }

    private Map<String, Object> invalidArguments(List<String> errors) {
        List<Map<String, Object>> issues = errors.stream()
                .map(message -> issue("INVALID_TOOL_ARGUMENTS", message,
                        "按工具 JSON Schema 修正参数后重试"))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("issues", issues);
        return result;
    }

    private Map<String, Object> failure(String code, String message, String hint) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("issues", List.of(issue(code, message, hint)));
        return result;
    }

    private Map<String, Object> issue(String code, String message, String hint) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("level", "ERROR");
        issue.put("code", code);
        issue.put("message", message);
        issue.put("hint", hint);
        return issue;
    }

    private boolean resultSucceeded(Object result) {
        return !(result instanceof Map<?, ?> map) || !Boolean.FALSE.equals(map.get("ok"));
    }

    private String stringifyResult(Object result) {
        if (result == null) {
            return "";
        }
        String text;
        if (result instanceof String value) {
            text = value;
        } else {
            try {
                text = objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                text = String.valueOf(result);
            }
        }
        return text.length() <= RESULT_CONTENT_LIMIT
                ? text : text.substring(0, RESULT_CONTENT_LIMIT) + "...(截断)";
    }

    private void publish(HarnessEventSink sink, String type, Map<String, Object> payload) {
        if (sink != null && !sink.isTerminated()) {
            sink.publish(HarnessEvent.of(type, payload));
        }
    }

    private static Map<String, Object> emptyObjectSchema() {
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

    private static final class InvalidToolArgumentsException extends RuntimeException {
        private InvalidToolArgumentsException(String message) {
            super(message);
        }
    }

    private static final class UnauthorizedToolException extends RuntimeException {
        private UnauthorizedToolException(String message) {
            super(message);
        }
    }
}
