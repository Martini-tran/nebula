package com.nebula.manager.ai.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.manager.dto.AiChatStreamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 聊天控制器（管理员端）
 * 提供基于 SSE 的流式对话（{@code /admin/ai-chat/stream}）：把 {@link AiService#stream} 的增量回调
 * 转发为 SSE 事件流，事件名约定 {@code delta}（文本片段）/{@code done}（结束）/{@code error}（异常）。
 *
 * <p>注意：当前直连 {@link AiService#stream}，该路径不经过滤器链、不落库会话消息、不记调用日志
 * （与同步 {@code chat} 不同），多轮上下文由前端在 {@code messages} 中携带。
 *
 * @author nebula
 */
@Slf4j
@RestController
@RequestMapping("/admin/ai-chat")
@RequiredArgsConstructor
public class AiChatAdminController {

    /**
     * SSE 连接超时（毫秒）。超时后触发 onTimeout 并结束响应。
     */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final AiService aiService;

    /**
     * SIP 帧序列化用 JSON 处理器：自建实例而非容器注入
     * （容器走 Jackson 3，此处沿用本模块 Jackson 2 的 {@code com.fasterxml.jackson.databind.ObjectMapper} 约定）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式对话线程池：SseEmitter 需在独立线程写出，避免阻塞容器请求线程。
     */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ai-chat-sse");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 发起流式对话
     * 以 SSE 逐片推送模型输出，前端可据此实现打字机效果。
     *
     * @param request 聊天请求
     * @return SSE 发射器
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("manager:ai-chat:stream")
    public SseEmitter stream(@RequestBody AiChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 已终止标记：保证 complete/completeWithError 只调用一次，且超时/断连后不再写出
        AtomicBoolean terminated = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            terminated.set(true);
            emitter.complete();
        });
        emitter.onError(throwable -> terminated.set(true));

        streamExecutor.execute(() -> {
            try {
                aiService.stream(buildAiRequest(request), new SseCallback(emitter, terminated));
            } catch (RuntimeException e) {
                // stream 内部异常已由回调转为 error 事件，此处仅兜底未覆盖的抛出路径
                log.warn("AI流式对话执行失败: {}", e.getMessage());
                if (terminated.compareAndSet(false, true)) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    /**
     * 发起流式对话（OpenAI 兼容裸流 / SIP 模式）
     * 与 {@link #stream} 的差异仅在传输帧格式：本端点只写 {@code data:} 行、不带 {@code event:} 事件名，
     * 帧体沿用 OpenAI Chat Completions 增量结构，收尾以 {@code data: [DONE]} 标记。
     * 供前端 {@code useXStream} 的自定义 {@code transformStream}（SIP 模式）解析。
     *
     * <p>帧约定：
     * <pre>
     * data: {"choices":[{"delta":{"content":"片段"}}]}\n\n
     * data: {"error":{"message":"原因"}}\n\n   （异常时）
     * data: [DONE]\n\n                          （结束）
     * </pre>
     *
     * @param request 聊天请求（与 {@link #stream} 同构）
     * @return SSE 发射器
     */
    @PostMapping(value = "/stream-sip", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("manager:ai-chat:stream")
    public SseEmitter streamSip(@RequestBody AiChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean terminated = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            terminated.set(true);
            emitter.complete();
        });
        emitter.onError(throwable -> terminated.set(true));

        streamExecutor.execute(() -> {
            try {
                aiService.stream(buildAiRequest(request), new SipCallback(emitter, terminated, objectMapper));
            } catch (RuntimeException e) {
                // stream 内部异常已由回调转为 error 帧，此处仅兜底未覆盖的抛出路径
                log.warn("AI流式对话执行失败(SIP): {}", e.getMessage());
                if (terminated.compareAndSet(false, true)) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    /**
     * 组装 SDK 请求：优先使用历史消息构造完整上下文，否则退化为单条提示词。
     *
     * @param request 聊天请求
     * @return SDK 请求
     */
    private AiRequest buildAiRequest(AiChatStreamRequest request) {
        AiRequest aiRequest = new AiRequest();
        aiRequest.setStream(true);
        aiRequest.setConversationId(request.getConversationId());
        aiRequest.setModel(request.getModel());
        aiRequest.setTemperature(request.getTemperature());
        aiRequest.setPrompt(request.getPrompt());

        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            request.getMessages().stream()
                    .filter(message -> message != null && message.getContent() != null)
                    .forEach(message -> {
                        Map<String, Object> one = new LinkedHashMap<>();
                        one.put("role", message.getRole() == null ? "user" : message.getRole());
                        one.put("content", message.getContent());
                        messages.add(one);
                    });
        }
        // 历史消息非空时，把本轮提示词作为最后一条用户消息追加，形成完整上下文
        if (!messages.isEmpty() && request.getPrompt() != null && !request.getPrompt().isBlank()) {
            Map<String, Object> current = new LinkedHashMap<>();
            current.put("role", "user");
            current.put("content", request.getPrompt());
            messages.add(current);
        }
        aiRequest.setMessages(messages);
        return aiRequest;
    }

    /**
     * 把 SDK 流式回调转发为 SSE 事件。
     * 事件约定：{@code delta} 携带 {@code {content:"片段"}}，{@code done} 携带完整响应，
     * {@code error} 携带 {@code {message:"原因"}}。
     */
    @RequiredArgsConstructor
    private static class SseCallback implements AiCallback {

        private final SseEmitter emitter;

        private final AtomicBoolean terminated;

        @Override
        public void onDelta(String content, Map<String, Object> chunk) {
            if (terminated.get() || content == null || content.isEmpty()) {
                return;
            }
            send("delta", Map.of("content", content));
        }

        @Override
        public void onComplete(Map<String, Object> response) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("done")
                        .data(response == null ? Map.of() : response, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                log.debug("发送AI流式结束事件失败（客户端可能已断开）: {}", e.getMessage());
                emitter.complete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            String message = throwable == null ? "未知错误" : throwable.getMessage();
            log.warn("AI流式对话异常: {}", message);
            try {
                // 响应头已发出，无法再返回 HTTP 错误码，故以 error 事件通知前端后正常结束
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", message == null ? "未知错误" : message),
                                MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.debug("发送AI流式异常事件失败（客户端可能已断开）: {}", e.getMessage());
            }
            emitter.complete();
        }

        /**
         * 发送 SSE 事件，客户端断开等写出失败不向上抛出，仅标记终止
         *
         * @param name 事件名
         * @param data 事件数据
         */
        private void send(String name, Object data) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // 客户端断开：标记终止，让后续片段直接跳过
                terminated.set(true);
                log.debug("发送AI流式片段失败（客户端可能已断开）: {}", e.getMessage());
            }
        }
    }

    /**
     * 把 SDK 流式回调转发为 OpenAI 兼容裸流（SIP 模式）。
     * 每帧仅写 {@code data:} 行、不带事件名：
     * {@code delta} → {@code {choices:[{delta:{content:"片段"}}]}}，
     * {@code error} → {@code {error:{message:"原因"}}}，结束统一写 {@code [DONE]}。
     */
    private static class SipCallback implements AiCallback {

        private final SseEmitter emitter;

        private final AtomicBoolean terminated;

        private final ObjectMapper objectMapper;

        SipCallback(SseEmitter emitter, AtomicBoolean terminated, ObjectMapper objectMapper) {
            this.emitter = emitter;
            this.terminated = terminated;
            this.objectMapper = objectMapper;
        }

        @Override
        public void onDelta(String content, Map<String, Object> chunk) {
            if (terminated.get() || content == null || content.isEmpty()) {
                return;
            }
            // OpenAI Chat Completions 增量结构：choices[0].delta.content
            Map<String, Object> delta = Map.of("content", content);
            Map<String, Object> choice = Map.of("delta", delta);
            send(Map.of("choices", List.of(choice)));
        }

        @Override
        public void onComplete(Map<String, Object> response) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            // 内容已由 delta 逐帧下发，收尾仅需 [DONE] 标记（前端据此关闭流）
            sendRaw("[DONE]");
            emitter.complete();
        }

        @Override
        public void onError(Throwable throwable) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            String message = throwable == null ? "未知错误" : throwable.getMessage();
            log.warn("AI流式对话异常(SIP): {}", message);
            // 响应头已发出，无法再返回 HTTP 错误码，故以 error 帧通知前端后正常结束
            send(Map.of("error", Map.of("message", message == null ? "未知错误" : message)));
            emitter.complete();
        }

        /**
         * 发送一帧 JSON 数据：序列化后作为纯 {@code data:} 行写出（无 {@code event:} 事件名）。
         *
         * @param data 帧体
         */
        private void send(Object data) {
            try {
                sendRaw(objectMapper.writeValueAsString(data));
            } catch (JsonProcessingException e) {
                // 序列化失败属编码级异常，不应中断整条流，仅记录并跳过该帧
                log.warn("序列化AI流式SIP帧失败: {}", e.getMessage());
            }
        }

        /**
         * 写出一行裸帧：仅设置 {@code data} 而不设 {@code name}，使 SseEmitter 只产出 {@code data:} 行。
         * 客户端断开等写出失败不向上抛出，仅标记终止让后续片段跳过。
         *
         * @param data data 行文本（已是最终字符串，如 JSON 或 {@code [DONE]}）
         */
        private void sendRaw(String data) {
            if (terminated.get() && !"[DONE]".equals(data)) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (IOException | IllegalStateException e) {
                terminated.set(true);
                log.debug("发送AI流式SIP帧失败（客户端可能已断开）: {}", e.getMessage());
            }
        }
    }
}
