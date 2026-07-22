package com.nebula.manager.ai.copilot.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code generate_flow} 工具的入参 JSON Schema 构造器。
 * 对齐 {@code FlowDefinition} 结构，让模型据此输出合法的流程定义。从工具类分离，使工具类聚焦逻辑。
 *
 * @author nebula
 */
final class FlowSchema {

    private FlowSchema() {
    }

    /**
     * 构造 generate_flow 的 OpenAI function parameters（JSON Schema）。
     *
     * @return 入参 schema
     */
    static Map<String, Object> build() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("flowCode", "nodes", "edges"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("flowCode", str("流程编码，全局唯一（英文/下划线）。若已存在会被拒绝，请用新编码。"));
        props.put("name", str("流程名称"));
        props.put("description", str("流程描述"));
        props.put("version", intWithDefault("版本号，默认 1", 1));
        props.put("engineType", enumStr("执行内核：DAG（默认，一次跑完）或 STATE_MACHINE（可回跳/成环/挂起）",
                List.of("DAG", "STATE_MACHINE"), "DAG"));
        props.put("defaultProfileCode", str("默认模型档案编码，从 list_model_profiles 选取；节点未指定档案时使用"));
        props.put("nodes", nodesSchema());
        props.put("edges", edgesSchema());
        schema.put("properties", props);
        return schema;
    }

    private static Map<String, Object> nodesSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("required", List.of("nodeCode", "nodeType"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("nodeCode", str("节点编码，流程内唯一（= 画布节点ID）"));
        props.put("name", str("节点显示名"));
        props.put("nodeType", enumStr("节点类型，取值见 list_node_types",
                List.of("PROMPT", "TOOL", "START", "END", "IF", "JOIN", "LOOP", "AGENT", "AGENT_REACT"), null));
        props.put("systemPrompt", str("系统提示词模板，可空，#{var} 引用上下文键"));
        props.put("promptTemplate", str("用户提示词模板，PROMPT 节点必填，#{var} 引用上游节点 outputKey"));
        props.put("profileCode", str("引用的模型档案编码，空则用流程默认档案"));
        props.put("model", str("模型名称（覆盖档案），一般留空"));
        props.put("temperature", number("采样温度，一般留空"));
        props.put("inputMapping", object("输入映射：模板变量名 -> 上游上下文键（一般可省略，同名自动匹配）"));
        props.put("outputKey", str("产物写回上下文的键名，供下游节点用 #{key} 引用"));
        props.put("outputMode", enumStr("产物模式：TEXT 整段写入 / JSON 解析后逐键展开",
                List.of("TEXT", "JSON"), "TEXT"));
        props.put("toolCode", str("TOOL 节点引用的工具编码，从 list_tools 选取；仅 TOOL 节点需要"));
        props.put("x", number("画布横坐标，沿主流程从左到右递增（如 120,360,600...）"));
        props.put("y", number("画布纵坐标，同层节点用相同值"));
        item.put("properties", props);

        Map<String, Object> arr = new LinkedHashMap<>();
        arr.put("type", "array");
        arr.put("description", "节点列表；至少包含一个 START 和一个 END 节点");
        arr.put("items", item);
        return arr;
    }

    private static Map<String, Object> edgesSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("required", List.of("fromNode", "toNode"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fromNode", str("起点节点编码"));
        props.put("toNode", str("终点节点编码"));
        props.put("conditionExpr", str("出边条件（SpEL），IF 分流用；空表示无条件直达。空表达式的边作为 default"));
        item.put("properties", props);

        Map<String, Object> arr = new LinkedHashMap<>();
        arr.put("type", "array");
        arr.put("description", "有向边列表，用 nodeCode 连接节点");
        arr.put("items", item);
        return arr;
    }

    private static Map<String, Object> str(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> number(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "number");
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> object(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> intWithDefault(String description, int def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", description);
        m.put("default", def);
        return m;
    }

    private static Map<String, Object> enumStr(String description, List<String> values, String def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        m.put("enum", values);
        if (def != null) {
            m.put("default", def);
        }
        return m;
    }
}
