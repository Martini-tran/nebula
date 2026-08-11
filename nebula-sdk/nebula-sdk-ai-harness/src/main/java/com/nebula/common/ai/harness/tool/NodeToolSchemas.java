package com.nebula.common.ai.harness.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点 mutation 工具共享的扁平字段 Schema。
 */
final class NodeToolSchemas {

    private NodeToolSchemas() {
    }

    static Map<String, Object> fields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nodeCode", Map.of("type", "string"));
        fields.put("name", Map.of("type", "string"));
        fields.put("nodeType", Map.of("type", "string"));
        fields.put("systemPrompt", Map.of(
                "type", "string",
                "description", "PROMPT/AGENT_REACT 的系统提示词：稳定角色、规则和输出约束"));
        fields.put("promptTemplate", Map.of(
                "type", "string",
                "description", "PROMPT/AGENT_REACT 必填的用户提示词模板；用 {{变量名}} 引用上下文"));
        fields.put("profileCode", Map.of(
                "type", "string",
                "description", "PROMPT/AGENT_REACT 使用的模型档案编码，必须来自 list_model_profiles"));
        fields.put("provider", Map.of("type", "string"));
        fields.put("model", Map.of("type", "string"));
        fields.put("baseUrl", Map.of("type", "string"));
        fields.put("apiKey", Map.of("type", "string"));
        fields.put("temperature", Map.of(
                "type", "number", "description", "模型采样温度，范围 0 到 2"));
        fields.put("maxTokens", Map.of(
                "type", "integer", "description", "模型最大输出 token 数，必须为正整数"));
        fields.put("topP", Map.of("type", "number"));
        fields.put("timeoutMs", Map.of("type", "integer"));
        fields.put("stop", stringArray());
        fields.put("options", Map.of("type", "object"));
        fields.put("inputMapping", Map.of(
                "type", "object", "description", "模板变量名到上下文键的映射"));
        fields.put("outputKey", Map.of(
                "type", "string", "description", "节点产物写回上下文的语义化变量名"));
        fields.put("outputMode", Map.of(
                "type", "string", "enum", List.of("TEXT", "JSON"),
                "description", "模型输出模式；JSON 模式必须在提示词中明确 JSON 结构"));
        fields.put("nodeConfig", Map.of("type", "object"));
        fields.put("rememberTrace", Map.of("type", "boolean"));
        fields.put("sortNo", Map.of("type", "integer"));
        fields.put("stateType", Map.of("type", "string", "enum", List.of("ENTRY", "NORMAL", "TERMINAL")));
        fields.put("maxAttempts", Map.of("type", "integer"));
        fields.put("backoffMs", Map.of("type", "integer"));
        fields.put("retryOn", stringArray());
        fields.put("stateTimeoutMs", Map.of("type", "integer"));
        fields.put("onError", Map.of("type", "string", "enum", List.of("FAIL", "CONTINUE", "GOTO_STATE")));
        fields.put("errorState", Map.of("type", "string"));
        fields.put("suspend", Map.of("type", "boolean"));
        fields.put("awaitingEvents", stringArray());
        return fields;
    }

    static Map<String, Object> patchSchema() {
        Map<String, Object> patchFields = fields();
        patchFields.remove("nodeCode");
        return Map.of("type", "object", "properties", patchFields, "additionalProperties", false);
    }

    private static Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }
}
