package com.nebula.space.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具的 inputSchema（JSON Schema 对象）构造器
 *
 * <p>不允许多余参数：模型把 dueDate 写成 due_date 时直接报错，而不是悄悄被忽略。</p>
 */
final class McpInputSchema {

    private static final String DATE_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$";
    private static final String TIME_PATTERN = "^([01][0-9]|2[0-3]):[0-5][0-9]$";
    private static final String ID_PATTERN = "^[0-9]+$";

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    private McpInputSchema() {
    }

    static McpInputSchema object() {
        return new McpInputSchema();
    }

    McpInputSchema string(String name, String description) {
        return property(name, prop("string", description));
    }

    McpInputSchema date(String name, String description) {
        Map<String, Object> p = prop("string", description + "（yyyy-MM-dd）");
        p.put("pattern", DATE_PATTERN);
        return property(name, p);
    }

    McpInputSchema time(String name, String description) {
        Map<String, Object> p = prop("string", description + "（HH:mm）");
        p.put("pattern", TIME_PATTERN);
        return property(name, p);
    }

    /**
     * ID 统一按字符串收发：接口返回的 ID 就是字符串（避免前端丢精度），模型照抄回来即可
     */
    McpInputSchema id(String name, String description) {
        Map<String, Object> p = prop("string", description);
        p.put("pattern", ID_PATTERN);
        return property(name, p);
    }

    McpInputSchema integer(String name, String description, int min, int max) {
        Map<String, Object> p = prop("integer", description);
        p.put("minimum", min);
        p.put("maximum", max);
        return property(name, p);
    }

    McpInputSchema bool(String name, String description) {
        return property(name, prop("boolean", description));
    }

    McpInputSchema oneOf(String name, String description, String... values) {
        Map<String, Object> p = prop("string", description);
        p.put("enum", List.of(values));
        return property(name, p);
    }

    McpInputSchema strings(String name, String description) {
        Map<String, Object> p = prop("array", description);
        p.put("items", Map.of("type", "string"));
        return property(name, p);
    }

    McpInputSchema required(String... names) {
        required.addAll(List.of(names));
        return this;
    }

    Map<String, Object> build() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private McpInputSchema property(String name, Map<String, Object> p) {
        properties.put(name, p);
        return this;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
