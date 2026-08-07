package com.nebula.manager.ai.copilot;

import com.nebula.common.ai.harness.runtime.HarnessEvent;
import com.nebula.common.ai.harness.runtime.HarnessEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copilot SSE 事件出口（薄封装）。
 * 把流程设计助手工具循环产生的各类事件写出为 SSE，事件约定：
 * {@code delta}（文本片段）/{@code tool_call}（工具调用进度）/{@code flow}（流程落库产物）/
 * {@code agent}（Agent 派生产物）/{@code done}（结束）/{@code error}（异常）。
 *
 * <p>内部持 {@link AtomicBoolean} 终止标记：保证 complete/completeWithError 只调用一次，
 * 且客户端断连/超时后不再写出（写失败即置终止，后续事件直接跳过）。
 *
 * @author nebula
 */
@Slf4j
public class CopilotSseSink implements HarnessEventSink {

    private static final Map<String, String> LEGACY_PRODUCT_EVENTS = Map.of(
            "generate_flow", "flow",
            "derive_agent", "agent");

    private final SseEmitter emitter;

    private final AtomicBoolean terminated;

    public CopilotSseSink(SseEmitter emitter, AtomicBoolean terminated) {
        this.emitter = emitter;
        this.terminated = terminated;
    }

    /**
     * 将 SDK Harness 事件投影为既有前端 SSE 协议。
     *
     * <p>审计事件已由 SDK 持久化，此处仅保留可观测日志；工具原始结果不直接混入 tool_call 事件，
     * 写库产物仍按既有 flow / agent 事件单独发送，保持前端零改动。
     */
    @Override
    @SuppressWarnings("unchecked")
    public void publish(HarnessEvent event) {
        if (event == null || terminated.get()) {
            return;
        }
        Map<String, Object> payload = event.payload();
        switch (event.type()) {
            case HarnessEvent.TEXT_DELTA -> delta(string(payload.get("content")));
            case HarnessEvent.TOOL_STARTED -> toolCall(clientToolPayload(payload));
            case HarnessEvent.TOOL_COMPLETED -> {
                toolCall(clientToolPayload(payload));
                emitLegacyProduct(payload);
            }
            case HarnessEvent.TOOL_AUDITED -> log.debug(
                    "Harness 工具审计事件: requestId={}, tool={}, success={}, latencyMs={}",
                    payload.get("requestId"), payload.get("toolCode"),
                    payload.get("success"), payload.get("latencyMs"));
            case HarnessEvent.CONVERSATION_COMPLETED -> {
                Object response = payload.get("response");
                done(response instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
            }
            case HarnessEvent.CONVERSATION_FAILED -> error(string(payload.get("message")));
            case "flow.committed" -> flow(new LinkedHashMap<>(payload));
            default -> log.debug("忽略未映射的 Harness 事件: {}", event.type());
        }
    }

    /**
     * 文本片段（终止轮最终回复分片）
     *
     * @param content 文本片段
     */
    public void delta(String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        send("delta", Map.of("content", content));
    }

    /**
     * 工具调用进度
     *
     * @param payload {@code {status, name, arguments?, resultBrief?}}
     */
    public void toolCall(Map<String, Object> payload) {
        send("tool_call", payload);
    }

    /**
     * 流程落库产物
     *
     * @param payload {@code {flowCode, version, name, definition?}}
     */
    public void flow(Map<String, Object> payload) {
        send("flow", payload);
    }

    /**
     * Agent 派生产物
     *
     * @param payload {@code {agentCode, id, name}}
     */
    public void agent(Map<String, Object> payload) {
        send("agent", payload);
    }

    /**
     * 正常结束：发送 done 事件并完成响应（只生效一次）。
     *
     * @param response 聚合响应
     */
    public void done(Map<String, Object> response) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("done")
                    .data(response == null ? Map.of() : response, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.debug("发送 copilot done 事件失败（客户端可能已断开）: {}", e.getMessage());
            emitter.complete();
        }
    }

    /**
     * 异常结束：以 error 事件通知前端后正常结束响应（只生效一次）。
     * 响应头已发出无法再返回 HTTP 错误码，故以事件传达。
     *
     * @param message 错误原因
     */
    public void error(String message) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("message", message == null ? "未知错误" : message), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("发送 copilot error 事件失败（客户端可能已断开）: {}", e.getMessage());
        }
        emitter.complete();
    }

    /**
     * 是否已终止（超时/断连/已结束）
     *
     * @return 是否终止
     */
    public boolean isTerminated() {
        return terminated.get();
    }

    private Map<String, Object> clientToolPayload(Map<String, Object> payload) {
        Map<String, Object> clientPayload = new LinkedHashMap<>(payload);
        clientPayload.remove("result");
        clientPayload.remove("latencyMs");
        clientPayload.remove("toolCallId");
        return clientPayload;
    }

    @SuppressWarnings("unchecked")
    private void emitLegacyProduct(Map<String, Object> payload) {
        String eventName = LEGACY_PRODUCT_EVENTS.get(string(payload.get("name")));
        Object result = payload.get("result");
        if (eventName == null || !(result instanceof Map<?, ?> map) || !Boolean.TRUE.equals(map.get("ok"))) {
            return;
        }
        Map<String, Object> product = new LinkedHashMap<>((Map<String, Object>) map);
        product.remove("ok");
        if ("flow".equals(eventName)) {
            flow(product);
        } else {
            agent(product);
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 发送一个 SSE 事件；客户端断开等写失败不向上抛出，仅标记终止让后续事件跳过。
     *
     * @param name 事件名
     * @param data 事件数据
     */
    private void send(String name, Object data) {
        if (terminated.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            terminated.set(true);
            log.debug("发送 copilot [{}] 事件失败（客户端可能已断开）: {}", name, e.getMessage());
        }
    }
}
