package com.nebula.common.ai.harness.tool;

import com.nebula.common.ai.flow.InvocationScope;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.harness.draft.DraftApplicationService;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 草稿工具公共元数据与 JSON Schema 构造器。
 *
 * @author nebula
 */
@RequiredArgsConstructor
abstract class AbstractDraftToolDefinition implements ToolDefinition {

    protected final DraftApplicationService service;

    @Override
    public final Set<InvocationScope> invocationScopes() {
        return Set.of(InvocationScope.COPILOT_TOOL);
    }

    @Override
    public final String category() {
        return "copilot";
    }

    protected static Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(required));
        schema.put("additionalProperties", false);
        return schema;
    }

    protected static Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    protected static Map<String, Object> integerSchema() {
        return Map.of("type", "integer");
    }

    protected static Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", stringSchema());
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> map(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    protected static List<String> strings(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    protected static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    protected static long longValue(Map<String, Object> params, String key) {
        return ((Number) params.get(key)).longValue();
    }

    protected static Integer integer(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    protected static Map<String, Object> without(Map<String, Object> params, String... excluded) {
        Map<String, Object> copy = new LinkedHashMap<>(params);
        for (String key : excluded) {
            copy.remove(key);
        }
        return copy;
    }
}
