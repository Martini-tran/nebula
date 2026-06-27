package com.nebula.blog.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程 JSON 列编解码工具
 * 负责节点实体上 {@code stop/options/input_mapping/node_config} 等 JSON 列与运行期
 * Map/List 之间的互转。解析失败一律容错为空集合，避免单条脏数据影响整张流程加载。
 *
 * <p>持有独立 {@link ObjectMapper}，不抢占应用主 Bean。
 *
 * @author nebula
 */
@Slf4j
public final class FlowJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FlowJsonCodec() {
    }

    /**
     * 反序列化为字符串映射（如 inputMapping）
     */
    public static Map<String, String> readStringMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("解析流程JSON(字符串映射)失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 反序列化为对象映射（如 options / nodeConfig）
     */
    public static Map<String, Object> readObjectMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析流程JSON(对象映射)失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 反序列化为字符串列表（如 stop）
     */
    public static List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<ArrayList<String>>() {
            });
        } catch (Exception e) {
            log.warn("解析流程JSON(字符串列表)失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 序列化为 JSON 文本；空集合返回 null（落库存 NULL）
     */
    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化流程JSON失败: {}", e.getMessage());
            return null;
        }
    }
}
