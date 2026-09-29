package com.nebula.scribe.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 字符串数组列（标签、主角名、别名）的 JSON 读写
 */
@Slf4j
public final class JsonLists {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    /**
     * 只读写字符串数组，不需要全局 Jackson 配置；
     * 容器里有多个 JsonMapper（含 redisJsonMapper），按类型注入会歧义，故自持一个。
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private JsonLists() {
    }

    /**
     * 去空白、去重后序列化；空列表存 null，避免库里出现大量 "[]"
     */
    public static String write(List<String> values) {
        List<String> cleaned = clean(values);
        return cleaned.isEmpty() ? null : JSON.writeValueAsString(cleaned);
    }

    /**
     * 解析失败按空列表处理，不让一行脏数据拖垮整页
     */
    public static List<String> read(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, STRING_LIST);
        } catch (JacksonException e) {
            log.warn("JSON 数组字段解析失败，按空处理: {}", json);
            return Collections.emptyList();
        }
    }

    /**
     * 去 null、去首尾空白、去空串、去重，保持原顺序
     */
    public static List<String> clean(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
