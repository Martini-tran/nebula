package com.nebula.space.mcp;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用的参数：按类型取值，取不到或格式不对抛 {@link IllegalArgumentException}，消息直接回给模型
 *
 * <p>参数先过了 SDK 按 inputSchema 做的校验，这里主要负责类型转换（ID 是字符串、日期是 yyyy-MM-dd）。</p>
 */
final class McpToolArgs {

    private final Map<String, Object> values;

    McpToolArgs(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    String string(String name) {
        Object value = values.get(name);
        return value == null ? null : String.valueOf(value);
    }

    String requiredString(String name) {
        String value = string(name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    LocalDate date(String name) {
        LocalDate value = optionalDate(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    LocalDate optionalDate(String name) {
        String value = string(name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " 应为 yyyy-MM-dd 格式的日期: " + value);
        }
    }

    Long id(String name) {
        Long value = optionalId(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    Long optionalId(String name) {
        String value = string(name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 应为数字 ID: " + value);
        }
    }

    Integer optionalInt(String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 应为整数: " + value);
        }
    }

    boolean bool(String name, boolean defaultValue) {
        Object value = values.get(name);
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 只取出请求里出现了的这些字段，原样（含 null）放进新 Map，用来转成局部保存的 DTO
     */
    Map<String, Object> pick(Collection<String> names) {
        Map<String, Object> picked = new LinkedHashMap<>();
        for (String name : names) {
            if (values.containsKey(name)) {
                picked.put(name, values.get(name));
            }
        }
        return picked;
    }
}
