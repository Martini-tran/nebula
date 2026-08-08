package com.nebula.common.ai.harness.draft;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.FlowNodeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 草稿工具扁平节点字段与运行时节点模型之间的转换器。
 *
 * <p>状态机治理字段在工具协议中保持扁平，在这里集中写入 {@code nodeConfig.stateConfig}，避免每个工具
 * 分别维护嵌套结构。
 *
 * @author nebula
 */
public class DraftNodeConverter {

    private static final Set<String> STATE_FIELDS = Set.of(
            "maxAttempts", "backoffMs", "retryOn", "stateTimeoutMs", "onError",
            "errorState", "suspend", "awaitingEvents");

    private static final Set<String> CLEARABLE_FIELDS = Set.of(
            "name", "systemPrompt", "promptTemplate", "profileCode", "provider", "model", "baseUrl", "apiKey",
            "temperature", "maxTokens", "topP", "timeoutMs", "stop", "options", "inputMapping", "outputKey",
            "nodeConfig", "stateType", "maxAttempts", "backoffMs", "retryOn", "stateTimeoutMs", "onError",
            "errorState", "suspend", "awaitingEvents");

    private final ObjectMapper objectMapper;

    public DraftNodeConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public FlowNodeDefinition create(Map<String, Object> fields) {
        FlowNodeDefinition node = new FlowNodeDefinition();
        apply(node, fields, true);
        return node;
    }

    public List<DraftIssue> patch(FlowNodeDefinition node,
                                  Map<String, Object> patch,
                                  List<String> clearFields) {
        List<DraftIssue> issues = new ArrayList<>();
        Set<String> clears = clearFields == null ? Set.of() : new LinkedHashSet<>(clearFields);
        for (String field : clears) {
            if (!CLEARABLE_FIELDS.contains(field)) {
                issues.add(DraftIssue.error("INVALID_TOOL_ARGUMENTS", node.getNodeCode(), field,
                        "字段不允许清空: " + field, "从 clearFields 移除该字段"));
            }
            if (patch != null && patch.containsKey(field)) {
                issues.add(DraftIssue.error("INVALID_TOOL_ARGUMENTS", node.getNodeCode(), field,
                        "同一字段不能同时出现在 patch 与 clearFields", "只保留一种更新方式"));
            }
        }
        if (!issues.isEmpty()) {
            return issues;
        }
        for (String field : clears) {
            clear(node, field);
        }
        try {
            apply(node, patch, false);
        } catch (IllegalArgumentException e) {
            issues.add(DraftIssue.error("INVALID_TOOL_ARGUMENTS", node.getNodeCode(), null,
                    e.getMessage(), "按字段类型修正 patch 后重试"));
        }
        return issues;
    }

    private void apply(FlowNodeDefinition node, Map<String, Object> fields, boolean allowNodeCode) {
        if (fields == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                throw new IllegalArgumentException("patch 不接受 null，请使用 clearFields 清空字段: " + field);
            }
            switch (field) {
                case "nodeCode" -> {
                    if (!allowNodeCode) {
                        throw new IllegalArgumentException("nodeCode 不允许通过 update_node 修改");
                    }
                    node.setNodeCode(text(value));
                }
                case "name" -> node.setName(text(value));
                case "nodeType" -> node.setNodeType(upper(value));
                case "systemPrompt" -> node.setSystemPrompt(text(value));
                case "promptTemplate" -> node.setPromptTemplate(text(value));
                case "profileCode" -> node.setProfileCode(text(value));
                case "provider" -> node.setProvider(text(value));
                case "model" -> node.setModel(text(value));
                case "baseUrl" -> node.setBaseUrl(text(value));
                case "apiKey" -> node.setApiKey(text(value));
                case "temperature" -> node.setTemperature(number(value, Double.class));
                case "maxTokens" -> node.setMaxTokens(number(value, Integer.class));
                case "topP" -> node.setTopP(number(value, Double.class));
                case "timeoutMs" -> node.setTimeoutMs(number(value, Integer.class));
                case "stop" -> node.setStop(convert(value, new TypeReference<List<String>>() { }));
                case "options" -> node.setOptions(convert(value, new TypeReference<Map<String, Object>>() { }));
                case "inputMapping" -> node.setInputMapping(convert(value,
                        new TypeReference<Map<String, String>>() { }));
                case "outputKey" -> node.setOutputKey(text(value));
                case "outputMode" -> node.setOutputMode(upper(value));
                case "nodeConfig" -> {
                    Map<String, Object> config = convert(value, new TypeReference<Map<String, Object>>() { });
                    if (config.containsKey("stateConfig")) {
                        throw new IllegalArgumentException("stateConfig 不能直接嵌套传入，请使用扁平状态机字段");
                    }
                    node.setNodeConfig(config);
                }
                case "rememberTrace" -> node.setRememberTrace(convert(value, Boolean.class));
                case "stateType" -> node.setStateType(upper(value));
                case "sortNo" -> node.setSortNo(number(value, Integer.class));
                default -> {
                    if (STATE_FIELDS.contains(field)) {
                        applyStateField(node, field, value);
                    } else {
                        throw new IllegalArgumentException("未知节点字段: " + field);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyStateField(FlowNodeDefinition node, String field, Object value) {
        Map<String, Object> nodeConfig = mutableMap(node.getNodeConfig());
        Map<String, Object> stateConfig = mutableMap((Map<String, Object>) nodeConfig.get("stateConfig"));
        Map<String, Object> retry = mutableMap((Map<String, Object>) stateConfig.get("retry"));
        switch (field) {
            case "maxAttempts" -> retry.put("maxAttempts", number(value, Integer.class));
            case "backoffMs" -> retry.put("backoffMs", number(value, Long.class));
            case "retryOn" -> retry.put("on", convert(value, new TypeReference<List<String>>() { }));
            case "stateTimeoutMs" -> stateConfig.put("timeoutMs", number(value, Long.class));
            case "onError" -> stateConfig.put("onError", upper(value));
            case "errorState" -> stateConfig.put("errorState", text(value));
            case "suspend" -> stateConfig.put("suspend", convert(value, Boolean.class));
            case "awaitingEvents" -> stateConfig.put("awaitingEvents",
                    convert(value, new TypeReference<List<String>>() { }));
            default -> throw new IllegalArgumentException("未知状态机字段: " + field);
        }
        if (!retry.isEmpty()) {
            stateConfig.put("retry", retry);
        }
        nodeConfig.put("stateConfig", stateConfig);
        node.setNodeConfig(nodeConfig);
    }

    @SuppressWarnings("unchecked")
    private void clear(FlowNodeDefinition node, String field) {
        switch (field) {
            case "name" -> node.setName(null);
            case "systemPrompt" -> node.setSystemPrompt(null);
            case "promptTemplate" -> node.setPromptTemplate(null);
            case "profileCode" -> node.setProfileCode(null);
            case "provider" -> node.setProvider(null);
            case "model" -> node.setModel(null);
            case "baseUrl" -> node.setBaseUrl(null);
            case "apiKey" -> node.setApiKey(null);
            case "temperature" -> node.setTemperature(null);
            case "maxTokens" -> node.setMaxTokens(null);
            case "topP" -> node.setTopP(null);
            case "timeoutMs" -> node.setTimeoutMs(null);
            case "stop" -> node.setStop(new ArrayList<>());
            case "options" -> node.setOptions(new LinkedHashMap<>());
            case "inputMapping" -> node.setInputMapping(new LinkedHashMap<>());
            case "outputKey" -> node.setOutputKey(null);
            case "nodeConfig" -> node.setNodeConfig(new LinkedHashMap<>());
            case "stateType" -> node.setStateType(null);
            default -> {
                Map<String, Object> nodeConfig = mutableMap(node.getNodeConfig());
                Map<String, Object> stateConfig = mutableMap((Map<String, Object>) nodeConfig.get("stateConfig"));
                Map<String, Object> retry = mutableMap((Map<String, Object>) stateConfig.get("retry"));
                switch (field) {
                    case "maxAttempts" -> retry.remove("maxAttempts");
                    case "backoffMs" -> retry.remove("backoffMs");
                    case "retryOn" -> retry.remove("on");
                    case "stateTimeoutMs" -> stateConfig.remove("timeoutMs");
                    case "onError" -> stateConfig.remove("onError");
                    case "errorState" -> stateConfig.remove("errorState");
                    case "suspend" -> stateConfig.remove("suspend");
                    case "awaitingEvents" -> stateConfig.remove("awaitingEvents");
                    default -> throw new IllegalArgumentException("字段不允许清空: " + field);
                }
                if (retry.isEmpty()) {
                    stateConfig.remove("retry");
                } else {
                    stateConfig.put("retry", retry);
                }
                if (stateConfig.isEmpty()) {
                    nodeConfig.remove("stateConfig");
                } else {
                    nodeConfig.put("stateConfig", stateConfig);
                }
                node.setNodeConfig(nodeConfig);
            }
        }
    }

    private Map<String, Object> mutableMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private String text(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("字段值必须是字符串");
        }
        return text;
    }

    private String upper(Object value) {
        return text(value).trim().toUpperCase();
    }

    private <T> T number(Object value, Class<T> type) {
        T converted = convert(value, type);
        if (!(converted instanceof Number)) {
            throw new IllegalArgumentException("字段值必须是数字");
        }
        return converted;
    }

    private <T> T convert(Object value, Class<T> type) {
        try {
            return objectMapper.convertValue(value, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("字段类型不正确: " + e.getMessage(), e);
        }
    }

    private <T> T convert(Object value, TypeReference<T> type) {
        try {
            return objectMapper.convertValue(value, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("字段类型不正确: " + e.getMessage(), e);
        }
    }
}
