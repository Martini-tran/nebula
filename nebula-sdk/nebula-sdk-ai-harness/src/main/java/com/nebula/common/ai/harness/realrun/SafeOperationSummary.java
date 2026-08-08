package com.nebula.common.ai.harness.realrun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.harness.config.HarnessRealRunProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** 对真实产物做保留键剔除、敏感字段脱敏和有界投影。 */
final class SafeOperationSummary {

    private static final List<String> SENSITIVE_PARTS = List.of(
            "apikey", "api_key", "token", "password", "secret", "authorization", "credential");

    private final ObjectMapper objectMapper;
    private final HarnessRealRunProperties properties;

    SafeOperationSummary(ObjectMapper objectMapper, HarnessRealRunProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    Map<String, Object> summarize(Map<String, Object> output) {
        AtomicInteger entries = new AtomicInteger();
        Object sanitized = sanitize(output == null ? Map.of() : output, 0, entries);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = sanitized instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of("value", sanitized);
        try {
            if (objectMapper.writeValueAsBytes(result).length <= properties.getMaxResultBytes()) {
                return result;
            }
        } catch (Exception ignored) {
            // 下面返回只包含键名的安全退化摘要。
        }
        return Map.of(
                "truncated", true,
                "reason", "RESULT_SIZE_LIMIT",
                "outputKeys", new ArrayList<>(result.keySet()));
    }

    private Object sanitize(Object value, int depth, AtomicInteger entries) {
        if (depth >= properties.getMaxResultDepth()) {
            return "[MAX_DEPTH]";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entries.incrementAndGet() > properties.getMaxResultEntries()) {
                    copy.put("truncated", true);
                    break;
                }
                String key = String.valueOf(entry.getKey());
                if (key.startsWith("__")) {
                    continue;
                }
                copy.put(key, sensitive(key) ? "[REDACTED]" : sanitize(entry.getValue(), depth + 1, entries));
            }
            return copy;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            for (Object item : iterable) {
                if (entries.incrementAndGet() > properties.getMaxResultEntries()) {
                    copy.add("[TRUNCATED]");
                    break;
                }
                copy.add(sanitize(item, depth + 1, entries));
            }
            return copy;
        }
        if (value instanceof String text && text.length() > properties.getMaxResultStringLength()) {
            return text.substring(0, properties.getMaxResultStringLength()) + "...[TRUNCATED]";
        }
        return value;
    }

    private boolean sensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "");
        return SENSITIVE_PARTS.stream().anyMatch(normalized::contains);
    }
}
