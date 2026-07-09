package com.nebula.common.ai.flow.tool;

import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回声工具（内置示例）
 * 把入参 {@code text} 原样返回，用于打通「工具节点 → 工具注册表 → 启动同步进 ai_tool 表」的最小闭环，
 * 也作为新增工具的范例：实现 {@link ToolDefinition} 并注册为 Bean 即可。
 *
 * @author nebula
 */
public class EchoToolDefinition implements ToolDefinition {

    @Override
    public String code() {
        return "echo";
    }

    @Override
    public String name() {
        return "回声";
    }

    @Override
    public String description() {
        return "把入参 text 原样返回，用于流程联调与工具机制示例";
    }

    @Override
    public String category() {
        return "util";
    }

    @Override
    public Map<String, Object> paramsSchema() {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "string");
        text.put("description", "待回显的文本");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", text);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("text"));
        return schema;
    }

    @Override
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        Object text = params == null ? null : params.get("text");
        return text == null ? "" : String.valueOf(text);
    }
}
