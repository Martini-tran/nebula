package com.nebula.common.ai.flow.store;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程定义转换器
 * 在持久化实体（AiFlow/AiFlowNode/AiFlowEdge）与 SDK 运行期定义
 * （FlowDefinition/FlowNodeDefinition/FlowEdgeDefinition）之间双向映射。
 * 读路径供 {@link DatabaseFlowDefinitionRepository} 组装定义；写路径供业务保存整图。
 *
 * @author nebula
 */
public final class FlowDefinitionConverter {

    private FlowDefinitionConverter() {
    }

    /* ===================== 实体 -> SDK 定义（读） ===================== */

    /**
     * 流程头实体转 SDK 定义（不含节点/边）
     */
    public static FlowDefinition toDefinition(AiFlow flow) {
        FlowDefinition def = new FlowDefinition();
        def.setFlowCode(flow.getFlowCode());
        def.setName(flow.getName());
        def.setDescription(flow.getDescription());
        def.setVersion(flow.getVersion() == null ? 1 : flow.getVersion());
        def.setDefaultProfileCode(flow.getDefaultProfileCode());
        def.setEngineType(flow.getEngineType() == null ? "DAG" : flow.getEngineType());
        def.setMaxTransitions(flow.getMaxTransitions() == null ? 100 : flow.getMaxTransitions());
        def.setMaxAgentDepth(flow.getMaxAgentDepth() == null ? 8 : flow.getMaxAgentDepth());
        def.setWebhookUrl(flow.getWebhookUrl());
        return def;
    }

    /**
     * 节点实体转 SDK 节点定义
     */
    public static FlowNodeDefinition toNodeDefinition(AiFlowNode entity) {
        FlowNodeDefinition node = new FlowNodeDefinition();
        node.setNodeCode(entity.getNodeCode());
        node.setName(entity.getName());
        node.setNodeType(entity.getNodeType() == null ? "PROMPT" : entity.getNodeType());
        node.setStateType(entity.getStateType());
        node.setSystemPrompt(entity.getSystemPrompt());
        node.setPromptTemplate(entity.getPromptTemplate());
        node.setProfileCode(entity.getProfileCode());
        node.setProvider(entity.getProvider());
        node.setModel(entity.getModel());
        node.setBaseUrl(entity.getBaseUrl());
        node.setApiKey(entity.getApiKey());
        node.setTemperature(entity.getTemperature());
        node.setMaxTokens(entity.getMaxTokens());
        node.setTopP(entity.getTopP());
        node.setTimeoutMs(entity.getTimeoutMs());
        node.setStop(FlowJsonCodec.readStringList(entity.getStop()));
        node.setOptions(FlowJsonCodec.readObjectMap(entity.getOptions()));
        node.setInputMapping(FlowJsonCodec.readStringMap(entity.getInputMapping()));
        node.setOutputKey(entity.getOutputKey());
        node.setOutputMode(entity.getOutputMode() == null ? "TEXT" : entity.getOutputMode());
        node.setNodeConfig(FlowJsonCodec.readObjectMap(entity.getNodeConfig()));
        node.setRememberTrace(Boolean.TRUE.equals(entity.getRememberTrace()));
        node.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        // 前端把各类型配置嵌在 nodeConfig.<type> 下（llm/tool），而执行器读顶层扁平字段/平铺键：
        // 这里按类型把嵌套配置摊平到执行器读取的位置，仅在顶层为空时填充（不覆盖已有扁平值）。
        flattenNodeConfig(node);
        return node;
    }

    /**
     * 把前端嵌套的 {@code nodeConfig.<type>} 配置摊平到后端执行器读取的字段/键。
     * 仅补空——若顶层已有值（如直接建的扁平节点），保留不动。存量数据无需迁移，读时即时对齐。
     *
     * <ul>
     *   <li>LLM/PROMPT：{@code nodeConfig.llm} → systemPrompt/promptTemplate/provider/model/baseUrl/
     *       profileCode/temperature/topP/maxTokens/outputMode/inputMapping</li>
     *   <li>TOOL：{@code nodeConfig.tool} → nodeConfig.toolCode（平铺，ToolNodeExecutor 读此键）/
     *       inputMapping/outputKey</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static void flattenNodeConfig(FlowNodeDefinition node) {
        Map<String, Object> cfg = node.getNodeConfig();
        if (cfg == null || cfg.isEmpty()) {
            return;
        }
        String type = node.getNodeType();
        if ("PROMPT".equals(type) || "LLM".equals(type)) {
            flattenLlm(node, asMap(cfg.get("llm")));
        } else if ("TOOL".equals(type)) {
            flattenTool(node, cfg, asMap(cfg.get("tool")));
        }
    }

    /** LLM：把 nodeConfig.llm.{prompt,model,parameters,output} 摊平到顶层空字段 */
    private static void flattenLlm(FlowNodeDefinition node, Map<String, Object> llm) {
        if (llm.isEmpty()) {
            return;
        }
        Map<String, Object> prompt = asMap(llm.get("prompt"));
        Map<String, Object> model = asMap(llm.get("model"));
        Map<String, Object> basic = asMap(asMap(llm.get("parameters")).get("basic"));
        Map<String, Object> output = asMap(llm.get("output"));

        if (isBlank(node.getSystemPrompt())) {
            node.setSystemPrompt(str(prompt.get("systemPrompt")));
        }
        if (isBlank(node.getPromptTemplate())) {
            node.setPromptTemplate(str(prompt.get("userPromptTemplate")));
        }
        if (isBlank(node.getProfileCode())) {
            node.setProfileCode(str(model.get("profileCode")));
        }
        if (isBlank(node.getProvider())) {
            node.setProvider(str(model.get("provider")));
        }
        if (isBlank(node.getModel())) {
            node.setModel(str(model.get("model")));
        }
        if (isBlank(node.getBaseUrl())) {
            node.setBaseUrl(str(model.get("baseUrl")));
        }
        if (node.getTemperature() == null) {
            node.setTemperature(dbl(basic.get("temperature")));
        }
        if (node.getTopP() == null) {
            node.setTopP(dbl(basic.get("topP")));
        }
        if (node.getMaxTokens() == null) {
            node.setMaxTokens(intg(basic.get("maxTokens")));
        }
        // 前端输出类型 MARKDOWN/TEXT/JSON：仅 JSON 触发后端逐键展开，其余整段写入，故非 JSON 归一为 TEXT
        if (isBlank(node.getOutputMode()) || "TEXT".equals(node.getOutputMode())) {
            String t = str(output.get("type"));
            node.setOutputMode("JSON".equalsIgnoreCase(t) ? "JSON" : "TEXT");
        }
        // 提示词变量映射（模板变量名 → 上下文键）：仅在顶层未配时采用
        Map<String, Object> vars = asMap(prompt.get("variables"));
        if ((node.getInputMapping() == null || node.getInputMapping().isEmpty()) && !vars.isEmpty()) {
            Map<String, String> mapping = new LinkedHashMap<>();
            vars.forEach((k, v) -> {
                if (k != null && v != null) {
                    mapping.put(k, String.valueOf(v));
                }
            });
            node.setInputMapping(mapping);
        }
    }

    /** TOOL：把 nodeConfig.tool.{toolCode,input.mapping,output.key} 摊平到执行器读取处 */
    private static void flattenTool(FlowNodeDefinition node, Map<String, Object> cfg, Map<String, Object> tool) {
        if (tool.isEmpty()) {
            return;
        }
        // ToolNodeExecutor 读 nodeConfig.toolCode（平铺键），前端存 nodeConfig.tool.toolCode
        if (isBlank(str(cfg.get("toolCode")))) {
            String toolCode = str(tool.get("toolCode"));
            if (!isBlank(toolCode)) {
                cfg.put("toolCode", toolCode);
            }
        }
        // 入参映射：前端 nodeConfig.tool.input.mapping → 顶层 inputMapping（执行器读 node.getInputMapping）
        Map<String, Object> inputMapping = asMap(asMap(tool.get("input")).get("mapping"));
        if ((node.getInputMapping() == null || node.getInputMapping().isEmpty()) && !inputMapping.isEmpty()) {
            Map<String, String> mapping = new LinkedHashMap<>();
            inputMapping.forEach((k, v) -> {
                if (k != null && v != null) {
                    mapping.put(k, String.valueOf(v));
                }
            });
            node.setInputMapping(mapping);
        }
        // 输出键：前端 nodeConfig.tool.output.key → 顶层 outputKey
        if (isBlank(node.getOutputKey())) {
            node.setOutputKey(str(asMap(tool.get("output")).get("key")));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Double dbl(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null ? null : Double.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intg(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? null : Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 边实体转 SDK 边定义
     */
    public static FlowEdgeDefinition toEdgeDefinition(AiFlowEdge entity) {
        FlowEdgeDefinition edge = new FlowEdgeDefinition();
        edge.setFromNode(entity.getFromNode());
        edge.setToNode(entity.getToNode());
        edge.setConditionExpr(entity.getConditionExpr());
        edge.setEventName(entity.getEventName());
        edge.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        return edge;
    }

    /* ===================== SDK 定义 -> 实体（写） ===================== */

    /**
     * SDK 节点定义转实体（归属指定流程编码）
     */
    public static AiFlowNode toNodeEntity(String flowCode, FlowNodeDefinition node) {
        AiFlowNode entity = new AiFlowNode();
        entity.setFlowCode(flowCode);
        entity.setNodeCode(node.getNodeCode());
        entity.setName(node.getName());
        entity.setNodeType(node.getNodeType() == null ? "PROMPT" : node.getNodeType());
        entity.setStateType(node.getStateType());
        entity.setSystemPrompt(node.getSystemPrompt());
        entity.setPromptTemplate(node.getPromptTemplate());
        entity.setProfileCode(node.getProfileCode());
        entity.setProvider(node.getProvider());
        entity.setModel(node.getModel());
        entity.setBaseUrl(node.getBaseUrl());
        entity.setApiKey(node.getApiKey());
        entity.setTemperature(node.getTemperature());
        entity.setMaxTokens(node.getMaxTokens());
        entity.setTopP(node.getTopP());
        entity.setTimeoutMs(node.getTimeoutMs());
        entity.setStop(FlowJsonCodec.write(node.getStop()));
        entity.setOptions(FlowJsonCodec.write(node.getOptions()));
        entity.setInputMapping(FlowJsonCodec.write(node.getInputMapping()));
        entity.setOutputKey(node.getOutputKey());
        entity.setOutputMode(node.getOutputMode() == null ? "TEXT" : node.getOutputMode());
        entity.setNodeConfig(FlowJsonCodec.write(node.getNodeConfig()));
        entity.setRememberTrace(node.isRememberTrace());
        entity.setSortNo(node.getSortNo());
        return entity;
    }

    /**
     * SDK 边定义转实体（归属指定流程编码）
     */
    public static AiFlowEdge toEdgeEntity(String flowCode, FlowEdgeDefinition edge) {
        AiFlowEdge entity = new AiFlowEdge();
        entity.setFlowCode(flowCode);
        entity.setFromNode(edge.getFromNode());
        entity.setToNode(edge.getToNode());
        entity.setConditionExpr(edge.getConditionExpr());
        entity.setEventName(edge.getEventName());
        entity.setSortNo(edge.getSortNo());
        return entity;
    }
}
