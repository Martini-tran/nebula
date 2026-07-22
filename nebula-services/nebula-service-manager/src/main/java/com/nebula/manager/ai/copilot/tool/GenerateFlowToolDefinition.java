package com.nebula.manager.ai.copilot.tool;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.ToolContext;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.service.FlowAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copilot 工具：根据结构化流程定义创建/覆盖一条 AI 流程并落库（核心写库工具）。
 * "流程设计助手"把用户需求转成本工具的入参（对齐 {@link FlowDefinition} 结构），工具做轻量校验后调
 * {@link FlowAdminService#save} 落 {@code ai_flow} 三表。校验失败不抛异常，而是返回 {@code {ok:false,error}}，
 * 让工具调用闭环把错误回灌给模型自愈（配合 maxIterations 限重试）。
 *
 * <p>约定：直写节点顶层扁平字段（promptTemplate/model/outputKey 等，扁平优先，直接可运行）；画布坐标
 * x/y 塞进 {@code nodeConfig.__x6}（前端回显用，执行器不读）；TOOL 节点的 toolCode 塞进 {@code nodeConfig.toolCode}。
 *
 * @author nebula
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateFlowToolDefinition implements ToolDefinition {

    private final FlowAdminService flowAdminService;

    @Override
    public String code() {
        return "generate_flow";
    }

    @Override
    public String name() {
        return "生成流程并落库";
    }

    @Override
    public String description() {
        return "根据结构化的流程定义创建一条 AI 流程并落库，成功返回 flowCode 与 version。"
                + "nodeCode 在流程内唯一；edges 用 nodeCode 连接；PROMPT 节点必须提供 promptTemplate；"
                + "至少包含一个 START 节点和一个 END 节点。若 flowCode 已存在会被拒绝，请换一个新的 flowCode。";
    }

    @Override
    public String category() {
        return ListNodeTypesToolDefinition.COPILOT_CATEGORY;
    }

    @Override
    public int sortNo() {
        return 20;
    }

    @Override
    public Map<String, Object> paramsSchema() {
        return FlowSchema.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Map<String, Object> params, ToolContext ctx) {
        if (params == null || params.isEmpty()) {
            return fail("入参为空，请提供 flowCode/nodes/edges");
        }
        String flowCode = str(params.get("flowCode"));
        if (isBlank(flowCode)) {
            return fail("flowCode 不能为空");
        }

        Object nodesObj = params.get("nodes");
        Object edgesObj = params.get("edges");
        if (!(nodesObj instanceof List) || ((List<?>) nodesObj).isEmpty()) {
            return fail("nodes 不能为空");
        }
        if (!(edgesObj instanceof List)) {
            return fail("edges 必须是数组");
        }

        // R8：flowCode 已存在则拒绝覆盖，让模型换 code（避免误覆盖线上流程）
        if (flowExists(flowCode)) {
            return fail("flowCode[" + flowCode + "] 已存在，请换一个新的 flowCode 以免覆盖已有流程");
        }

        FlowDefinition def = new FlowDefinition();
        def.setFlowCode(flowCode);
        def.setName(str(params.get("name")));
        def.setDescription(str(params.get("description")));
        Integer version = toInt(params.get("version"));
        if (version != null && version > 0) {
            def.setVersion(version);
        }
        String engineType = str(params.get("engineType"));
        if (!isBlank(engineType)) {
            def.setEngineType(engineType);
        }
        def.setDefaultProfileCode(str(params.get("defaultProfileCode")));

        List<FlowNodeDefinition> nodes = new ArrayList<>();
        for (Object item : (List<Object>) nodesObj) {
            if (item instanceof Map) {
                nodes.add(toNode((Map<String, Object>) item));
            }
        }
        List<FlowEdgeDefinition> edges = new ArrayList<>();
        for (Object item : (List<Object>) edgesObj) {
            if (item instanceof Map) {
                edges.add(toEdge((Map<String, Object>) item));
            }
        }
        def.setNodes(nodes);
        def.setEdges(edges);

        String error = validate(def);
        if (error != null) {
            return fail(error);
        }

        try {
            flowAdminService.save(def);
        } catch (BizException e) {
            return fail("落库失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.warn("generate_flow 落库异常: {}", e.getMessage());
            return fail("落库异常: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("flowCode", def.getFlowCode());
        result.put("version", def.getVersion());
        result.put("name", def.getName());
        result.put("nodeCount", nodes.size());
        // 附带完整定义，供 driver 直接推给前端就地回显画布（camelCase 序列化对齐 FlowDefinitionRaw）
        result.put("definition", def);
        return result;
    }

    /**
     * 把入参 node map 转 {@link FlowNodeDefinition}，直写顶层扁平字段（扁平优先，直接可运行）。
     */
    @SuppressWarnings("unchecked")
    private FlowNodeDefinition toNode(Map<String, Object> m) {
        FlowNodeDefinition node = new FlowNodeDefinition();
        node.setNodeCode(str(m.get("nodeCode")));
        node.setName(str(m.get("name")));
        String nodeType = str(m.get("nodeType"));
        if (!isBlank(nodeType)) {
            node.setNodeType(nodeType);
        }
        node.setSystemPrompt(str(m.get("systemPrompt")));
        node.setPromptTemplate(str(m.get("promptTemplate")));
        node.setProfileCode(str(m.get("profileCode")));
        node.setModel(str(m.get("model")));
        Double temperature = toDouble(m.get("temperature"));
        if (temperature != null) {
            node.setTemperature(temperature);
        }
        node.setOutputKey(str(m.get("outputKey")));
        String outputMode = str(m.get("outputMode"));
        if (!isBlank(outputMode)) {
            node.setOutputMode(outputMode);
        }
        if (m.get("inputMapping") instanceof Map<?, ?> im) {
            Map<String, String> mapping = new LinkedHashMap<>();
            im.forEach((k, v) -> {
                if (k != null && v != null) {
                    mapping.put(String.valueOf(k), String.valueOf(v));
                }
            });
            node.setInputMapping(mapping);
        }

        // TOOL 节点的工具编码落 nodeConfig.toolCode（ToolNodeExecutor 读此平铺键）
        String toolCode = str(m.get("toolCode"));
        if (!isBlank(toolCode)) {
            node.getNodeConfig().put("toolCode", toolCode);
        }
        // 画布坐标落 nodeConfig.__x6（纯前端回显，执行器不读）
        Double x = toDouble(m.get("x"));
        Double y = toDouble(m.get("y"));
        if (x != null || y != null) {
            Map<String, Object> pos = new LinkedHashMap<>();
            pos.put("x", x == null ? 0 : x);
            pos.put("y", y == null ? 0 : y);
            node.getNodeConfig().put("__x6", pos);
        }
        return node;
    }

    private FlowEdgeDefinition toEdge(Map<String, Object> m) {
        FlowEdgeDefinition edge = new FlowEdgeDefinition();
        edge.setFromNode(str(m.get("fromNode")));
        edge.setToNode(str(m.get("toNode")));
        edge.setConditionExpr(str(m.get("conditionExpr")));
        return edge;
    }

    /**
     * 轻量校验：nodeCode 唯一 / edge 引用存在 / 至少一个 START+END / PROMPT 节点有 promptTemplate。
     * 返回错误消息，null 表示通过。
     */
    private String validate(FlowDefinition def) {
        Set<String> codes = new HashSet<>();
        boolean hasStart = false;
        boolean hasEnd = false;
        for (FlowNodeDefinition node : def.getNodes()) {
            String code = node.getNodeCode();
            if (isBlank(code)) {
                return "存在缺少 nodeCode 的节点";
            }
            if (!codes.add(code)) {
                return "nodeCode 重复: " + code;
            }
            String type = node.getNodeType();
            if ("START".equals(type)) {
                hasStart = true;
            } else if ("END".equals(type)) {
                hasEnd = true;
            } else if ("PROMPT".equals(type) && isBlank(node.getPromptTemplate())) {
                return "PROMPT 节点[" + code + "]缺少 promptTemplate";
            }
        }
        if (!hasStart) {
            return "缺少 START 节点";
        }
        if (!hasEnd) {
            return "缺少 END 节点";
        }
        for (FlowEdgeDefinition edge : def.getEdges()) {
            if (isBlank(edge.getFromNode()) || isBlank(edge.getToNode())) {
                return "存在缺少 fromNode/toNode 的边";
            }
            if (!codes.contains(edge.getFromNode())) {
                return "边引用了不存在的 fromNode: " + edge.getFromNode();
            }
            if (!codes.contains(edge.getToNode())) {
                return "边引用了不存在的 toNode: " + edge.getToNode();
            }
        }
        return null;
    }

    /**
     * 探测 flowCode 是否已存在（getDefinition 不存在时抛 NOT_FOUND，据此判断）。
     */
    private boolean flowExists(String flowCode) {
        try {
            return flowAdminService.getDefinition(flowCode) != null;
        } catch (BizException e) {
            return false;
        } catch (RuntimeException e) {
            // 其它异常按不存在处理，把落库结果交给 save 决定
            return false;
        }
    }

    private Map<String, Object> fail(String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", error);
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private static Double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
