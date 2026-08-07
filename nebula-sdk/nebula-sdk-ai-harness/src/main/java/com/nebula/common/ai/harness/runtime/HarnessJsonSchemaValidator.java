package com.nebula.common.ai.harness.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Harness 工具参数所需的轻量 JSON Schema 校验器。
 *
 * <p>覆盖工具声明实际使用的 object/properties/required/items/type/enum/additionalProperties 约束。
 * 不支持的 schema 关键字保持开放，避免把元数据描述误判为运行错误。
 */
final class HarnessJsonSchemaValidator {

    private HarnessJsonSchemaValidator() {
    }

    static List<String> validate(Object value, Map<String, Object> schema) {
        List<String> errors = new ArrayList<>();
        validateAt(value, schema, "$", errors);
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static void validateAt(Object value, Map<String, Object> schema, String path, List<String> errors) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        String type = string(schema.get("type"));
        if (type != null && !matchesType(value, type)) {
            errors.add(path + " 应为 " + type + "，实际为 " + typeName(value));
            return;
        }

        Object enumValues = schema.get("enum");
        if (enumValues instanceof Collection<?> allowed && !allowed.contains(value)) {
            errors.add(path + " 必须是 " + allowed + " 之一");
        }

        if (value instanceof Map<?, ?> object) {
            Object required = schema.get("required");
            if (required instanceof Collection<?> names) {
                for (Object name : names) {
                    String field = string(name);
                    if (field != null && (!object.containsKey(field) || object.get(field) == null)) {
                        errors.add(path + "." + field + " 为必填字段");
                    }
                }
            }

            Map<String, Object> properties = schema.get("properties") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                String field = string(entry.getKey());
                Object fieldSchema = field == null ? null : properties.get(field);
                if (fieldSchema instanceof Map<?, ?> map) {
                    validateAt(entry.getValue(), (Map<String, Object>) map, path + "." + field, errors);
                } else if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                    errors.add(path + "." + field + " 不是允许的字段");
                }
            }
        }

        if (value instanceof List<?> list && schema.get("items") instanceof Map<?, ?> itemSchema) {
            for (int i = 0; i < list.size(); i++) {
                validateAt(list.get(i), (Map<String, Object>) itemSchema, path + "[" + i + "]", errors);
            }
        }
    }

    private static boolean matchesType(Object value, String type) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Number number
                    && !(number instanceof Float || number instanceof Double || number instanceof java.math.BigDecimal);
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "null" -> false;
            default -> true;
        };
    }

    private static String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value instanceof Number ? "number" : value.getClass().getSimpleName();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
