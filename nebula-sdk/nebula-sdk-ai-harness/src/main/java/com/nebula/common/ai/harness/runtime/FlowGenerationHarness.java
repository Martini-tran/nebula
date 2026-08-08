package com.nebula.common.ai.harness.runtime;

import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.conversation.HarnessExampleProvider;
import com.nebula.common.ai.harness.conversation.HarnessMessage;
import com.nebula.common.ai.harness.conversation.HarnessPromptProvider;
import com.nebula.common.ai.harness.conversation.HarnessRequest;
import com.nebula.common.ai.harness.conversation.HarnessToolContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程生成 Harness 主循环。
 *
 * <p>模型调用、工具反馈回灌和收敛边界统一收口在 SDK；宿主只负责请求转换、提示词/示例 SPI 与事件投影。
 * B4 起只注册细粒度草稿工具；高风险恢复动作使用结构化请求并绕过模型。
 *
 * @author nebula
 */
@Slf4j
public class FlowGenerationHarness {

    private static final int DELTA_CHUNK_SIZE = 24;
    private static final String REAL_RUN_DRAFT_TOOL = "real_run_draft";

    private final AiService aiService;
    private final HarnessToolScheduler toolScheduler;
    private final List<HarnessPromptProvider> promptProviders;
    private final List<HarnessExampleProvider> exampleProviders;
    private final int maxIterations;

    public FlowGenerationHarness(AiService aiService,
                                 HarnessToolScheduler toolScheduler,
                                 List<HarnessPromptProvider> promptProviders,
                                 List<HarnessExampleProvider> exampleProviders,
                                 int maxIterations) {
        this.aiService = aiService;
        this.toolScheduler = toolScheduler;
        this.promptProviders = promptProviders == null ? List.of() : List.copyOf(promptProviders);
        this.exampleProviders = exampleProviders == null ? List.of() : List.copyOf(exampleProviders);
        this.maxIterations = Math.max(1, maxIterations);
    }

    /**
     * 执行一次生成对话。错误通过事件契约返回，不把传输细节泄漏进 SDK。
     */
    public void run(HarnessRequest request, HarnessCallContext context, HarnessEventSink sink) {
        if (request == null || context == null || sink == null) {
            throw new IllegalArgumentException("request/context/sink 不能为空");
        }
        if (!context.authenticated()) {
            fail(sink, "缺少已认证用户身份");
            return;
        }

        try {
            HarnessToolContext toolContext = new HarnessToolContext(context);
            if (request.resumeAction() != null) {
                resumeConfirmedAction(request, toolContext, sink);
                return;
            }
            if (request.confirmationToken() != null && !request.confirmationToken().isBlank()) {
                fail(sink, "确认授权必须与结构化恢复动作同时提交");
                return;
            }
            runLoop(buildAiRequest(request, context), toolContext, sink);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("Flow Harness 执行失败: requestId={}, conversationId={}, error={}",
                    context.requestId(), context.sessionId(), message, e);
            fail(sink, message);
        }
    }

    private void resumeConfirmedAction(HarnessRequest request,
                                       HarnessToolContext toolContext,
                                       HarnessEventSink sink) {
        if (request.confirmationToken() == null || request.confirmationToken().isBlank()) {
            fail(sink, "恢复确认动作缺少服务端授权");
            return;
        }
        if (!REAL_RUN_DRAFT_TOOL.equals(request.resumeAction().toolCode())) {
            fail(sink, "不支持恢复该确认动作");
            return;
        }
        toolContext.put(HarnessToolContext.CONFIRMATION_TOKEN_ATTRIBUTE, request.confirmationToken());
        HarnessToolResult result = toolScheduler.executeResumed(
                request.resumeAction().toolCode(), request.resumeAction().arguments(), toolContext, sink);
        complete(Map.of(
                "resumed", true,
                "tool", result.toolCode(),
                "success", result.success()), false, sink);
    }

    private void runLoop(AiRequest request, HarnessToolContext toolContext, HarnessEventSink sink) {
        Map<String, Object> response = null;
        for (int iteration = 0; iteration < maxIterations && !sink.isTerminated(); iteration++) {
            response = aiService.chat(request);
            List<Map<String, Object>> toolCalls = extractToolCalls(response);
            if (toolCalls.isEmpty()) {
                emitFinalText(response, sink);
                complete(response, false, sink);
                return;
            }

            appendAssistantMessage(request, response);
            for (Map<String, Object> rawCall : toolCalls) {
                if (sink.isTerminated()) {
                    return;
                }
                HarnessToolCall call = toToolCall(rawCall);
                HarnessToolResult result = toolScheduler.execute(call, toolContext, sink);
                request.getMessages().add(toolMessage(result));
                if (requiresUserConfirmation(result)) {
                    complete(Map.of(
                            "awaitingConfirmation", true,
                            "tool", result.toolCode()), false, sink);
                    return;
                }
            }
        }

        if (!sink.isTerminated()) {
            if (response != null) {
                emitFinalText(response, sink);
                complete(response, true, sink);
            } else {
                fail(sink, "达到迭代上限仍未得到模型结果");
            }
        }
    }

    private AiRequest buildAiRequest(HarnessRequest request, HarnessCallContext context) {
        AiRequest aiRequest = new AiRequest();
        aiRequest.setRequestId(context.requestId());
        aiRequest.setUserId(context.userId());
        aiRequest.setConversationId(request.conversationId());
        aiRequest.setModel(request.model());
        aiRequest.setTemperature(request.temperature());

        List<Map<String, Object>> messages = new ArrayList<>();
        for (HarnessPromptProvider provider : promptProviders) {
            String prompt = provider.systemPrompt(request, context);
            if (prompt != null && !prompt.isBlank()) {
                messages.add(message("system", prompt));
            }
        }
        for (HarnessExampleProvider provider : exampleProviders) {
            List<String> examples = provider.examplePrompts(request, context);
            if (examples == null) {
                continue;
            }
            examples.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> messages.add(message("system", value)));
        }
        for (HarnessMessage history : request.messages()) {
            if (history != null && history.content() != null && !history.content().isBlank()) {
                messages.add(message(history.role() == null ? "user" : history.role(), history.content()));
            }
        }
        if (request.prompt() != null && !request.prompt().isBlank()) {
            messages.add(message("user", request.prompt()));
        }
        aiRequest.setMessages(messages);
        aiRequest.setTools(toolScheduler.toolSchemas(context));
        aiRequest.setToolChoice("auto");
        return aiRequest;
    }

    private void emitFinalText(Map<String, Object> response, HarnessEventSink sink) {
        String content = response == null ? null : string(response.get("content"));
        if (content == null || content.isEmpty()) {
            return;
        }
        for (int i = 0; i < content.length() && !sink.isTerminated(); i += DELTA_CHUNK_SIZE) {
            int end = Math.min(i + DELTA_CHUNK_SIZE, content.length());
            sink.publish(HarnessEvent.of(HarnessEvent.TEXT_DELTA,
                    Map.of("content", content.substring(i, end))));
        }
    }

    private void complete(Map<String, Object> response, boolean truncated, HarnessEventSink sink) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("response", response == null ? Map.of() : response);
        payload.put("truncated", truncated);
        sink.publish(HarnessEvent.of(HarnessEvent.CONVERSATION_COMPLETED, payload));
    }

    private void fail(HarnessEventSink sink, String message) {
        sink.publish(HarnessEvent.of(HarnessEvent.CONVERSATION_FAILED,
                Map.of("message", message == null ? "未知错误" : message)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(Map<String, Object> response) {
        Object value = response == null ? null : response.get("toolCalls");
        if (value instanceof List<?> list && !list.isEmpty()) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private void appendAssistantMessage(AiRequest request, Map<String, Object> response) {
        Object message = response == null ? null : response.get("assistantMessage");
        if (message instanceof Map<?, ?> map) {
            request.getMessages().add((Map<String, Object>) map);
        }
    }

    private HarnessToolCall toToolCall(Map<String, Object> call) {
        if (call == null) {
            return new HarnessToolCall(null, null, "{}");
        }
        return new HarnessToolCall(
                string(call.get("id")),
                string(call.get("name")),
                call.get("arguments") == null ? "{}" : string(call.get("arguments")));
    }

    private Map<String, Object> toolMessage(HarnessToolResult result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", result.callId() == null ? "" : result.callId());
        message.put("name", result.toolCode());
        message.put("content", result.content() == null ? "" : result.content());
        return message;
    }

    private boolean requiresUserConfirmation(HarnessToolResult result) {
        return result != null
                && result.result() instanceof Map<?, ?> map
                && "CONFIRM_REQUIRED".equals(map.get("code"));
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
