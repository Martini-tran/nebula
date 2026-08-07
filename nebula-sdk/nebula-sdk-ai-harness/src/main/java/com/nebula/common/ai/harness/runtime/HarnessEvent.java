package com.nebula.common.ai.harness.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Harness 运行事件。
 *
 * <p>事件名采用稳定的点分命名，宿主可投影为 SSE、WebSocket 或审计记录。payload 在构造时复制，防止
 * 发布后被调用方修改。
 *
 * @param type       事件类型
 * @param payload    结构化数据
 * @param occurredAt 发生时间
 * @author nebula
 */
public record HarnessEvent(String type, Map<String, Object> payload, Instant occurredAt) {

    public static final String TEXT_DELTA = "text.delta";
    public static final String TOOL_STARTED = "tool.started";
    public static final String TOOL_COMPLETED = "tool.completed";
    public static final String TOOL_AUDITED = "tool.audited";
    public static final String CONVERSATION_COMPLETED = "conversation.completed";
    public static final String CONVERSATION_FAILED = "conversation.failed";

    public HarnessEvent {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("事件类型不能为空");
        }
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static HarnessEvent of(String type, Map<String, Object> payload) {
        return new HarnessEvent(type, payload, Instant.now());
    }
}
