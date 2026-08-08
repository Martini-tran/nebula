package com.nebula.common.ai.harness.simulate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.draft.DraftIssue;

import java.util.List;
import java.util.Map;

/** 对可选 initialInput 执行字节数、条目数和嵌套深度限制。 */
public class SimulationInputValidator {

    private final ObjectMapper objectMapper;
    private final HarnessSimulationProperties properties;

    public SimulationInputValidator(ObjectMapper objectMapper, HarnessSimulationProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<DraftIssue> validate(Map<String, Object> input) {
        Map<String, Object> value = input == null ? Map.of() : input;
        if (entryCount(value) > properties.getMaxInitialInputEntries()) {
            return List.of(error("initialInput 条目总数超过 " + properties.getMaxInitialInputEntries()));
        }
        if (depth(value) > properties.getMaxInitialInputDepth()) {
            return List.of(error("initialInput 嵌套深度超过 " + properties.getMaxInitialInputDepth()));
        }
        try {
            if (objectMapper.writeValueAsBytes(value).length > properties.getMaxInitialInputBytes()) {
                return List.of(error("initialInput 超过 " + properties.getMaxInitialInputBytes() + " 字节"));
            }
        } catch (JsonProcessingException e) {
            return List.of(error("initialInput 不是可序列化的 JSON 对象"));
        }
        return List.of();
    }

    private int entryCount(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.size() + map.values().stream().mapToInt(this::entryCount).sum();
        }
        if (value instanceof Iterable<?> iterable) {
            int count = 0;
            for (Object item : iterable) {
                count += entryCount(item);
            }
            return count;
        }
        return 0;
    }

    private int depth(Object value) {
        if (value instanceof Map<?, ?> map) {
            return 1 + map.values().stream().mapToInt(this::depth).max().orElse(0);
        }
        if (value instanceof Iterable<?> iterable) {
            int max = 0;
            for (Object item : iterable) {
                max = Math.max(max, depth(item));
            }
            return 1 + max;
        }
        return 0;
    }

    private DraftIssue error(String message) {
        return DraftIssue.error("SIMULATION_INPUT_LIMIT", null, "initialInput", message,
                "缩小 initialInput 后重新模拟");
    }
}
