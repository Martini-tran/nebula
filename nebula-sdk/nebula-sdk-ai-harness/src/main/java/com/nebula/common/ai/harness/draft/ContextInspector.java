package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计算「某节点可以引用哪些上下文变量」。
 *
 * <p>与 {@code CommonRules} 的数据流校验同源：同样按支配关系（dominator）区分
 * 「必然可用」与「仅部分分支可用」，但方向相反——校验器回答「你引用的这个变量合法吗」，
 * 本类回答「你现在能引用什么」，供模型在**写模板之前**查询，把返工前移。
 *
 * <p>产出键一律取 {@code outputKey != blank ? outputKey : nodeCode}，与运行时执行器一致。
 *
 * @author nebula
 */
final class ContextInspector {

    /** 与 AiTemplateUtils 保持一致：{{var}} 与 #{var} 两种占位符都识别。 */
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}|#\\{\\s*([\\w.-]+)\\s*}");

    /** 会写回上下文的产物型节点。 */
    private static final Set<String> PRODUCER_TYPES = Set.of("PROMPT", "TOOL", "AGENT_REACT", "AGENT", "LOOP");

    private final FlowDefinition graph;

    private final Map<String, FlowNodeDefinition> nodeByCode = new HashMap<>();

    private final Map<String, Set<String>> predecessors = new HashMap<>();

    ContextInspector(FlowDefinition graph) {
        this.graph = graph;
        for (FlowNodeDefinition node : graph.getNodes()) {
            nodeByCode.put(node.getNodeCode(), node);
        }
        for (FlowEdgeDefinition edge : graph.getEdges()) {
            predecessors.computeIfAbsent(edge.getToNode(), key -> new HashSet<>()).add(edge.getFromNode());
        }
    }

    Map<String, Object> inspect(FlowNodeDefinition target) {
        Set<String> upstream = upstreamOf(target.getNodeCode());
        Set<String> dominators = dominatorsOf(target.getNodeCode());

        List<Map<String, Object>> guaranteed = new ArrayList<>();
        List<Map<String, Object>> conditional = new ArrayList<>();
        for (String code : upstream) {
            FlowNodeDefinition producer = nodeByCode.get(code);
            if (producer == null || !isProducer(producer)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("variable", effectiveOutputKey(producer));
            entry.put("fromNode", code);
            entry.put("nodeType", producer.getNodeType());
            entry.put("outputMode", producer.getOutputMode());
            if (dominators.contains(code)) {
                guaranteed.add(entry);
            } else {
                entry.put("note", "仅部分分支产出，引用会得到 TEMPLATE_VARIABLE_NOT_DOMINATED 警告");
                conditional.add(entry);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetNode", target.getNodeCode());
        payload.put("startInputs", startInputs());
        payload.put("guaranteed", guaranteed);
        payload.put("conditional", conditional);
        payload.put("referenced", referencedVariables(target));
        payload.put("modelConfig", modelConfig(target));
        payload.put("hint", guaranteed.isEmpty() && conditional.isEmpty() && startInputs().isEmpty()
                ? "该节点暂无可引用的上游变量；先 connect 上游节点，或在 START 的 nodeConfig.inputs 声明入参"
                : "在 promptTemplate / systemPrompt 中用 {{变量名}} 引用上表 variable");
        return payload;
    }

    /**
     * 回显该节点的模型配置来源，让模型看清「这个节点最终用什么参数跑」。
     *
     * <p>档案解析是三层的：节点 profileCode > 流程 defaultProfileCode > 全局兜底。
     * 节点上的 temperature 等字段又会逐项覆盖档案值（见 PromptNodeExecutor.buildRequest）。
     * 只看节点字段容易误判成「没配」，这里把继承来源一并说明。
     */
    private Map<String, Object> modelConfig(FlowNodeDefinition node) {
        Map<String, Object> config = new LinkedHashMap<>();
        boolean llm = node.getNodeType() != null
                && Set.of("PROMPT", "AGENT_REACT").contains(node.getNodeType().toUpperCase());
        config.put("appliesToNode", llm);
        if (!llm) {
            config.put("note", "非模型节点，不使用模型参数");
            return config;
        }
        String nodeProfile = node.getProfileCode();
        String flowProfile = graph.getDefaultProfileCode();
        config.put("profileCode", nodeProfile != null && !nodeProfile.isBlank() ? nodeProfile : flowProfile);
        config.put("profileSource", nodeProfile != null && !nodeProfile.isBlank() ? "node"
                : (flowProfile != null && !flowProfile.isBlank() ? "flowDefault" : "none"));
        putIfPresent(config, "temperature", node.getTemperature());
        putIfPresent(config, "maxTokens", node.getMaxTokens());
        putIfPresent(config, "topP", node.getTopP());
        putIfPresent(config, "timeoutMs", node.getTimeoutMs());
        config.put("outputMode", node.getOutputMode());
        config.put("hasSystemPrompt", node.getSystemPrompt() != null && !node.getSystemPrompt().isBlank());
        config.put("note", "节点未设置的采样参数继承自模型档案；档案编码用 list_model_profiles 获取");
        return config;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /** START 节点声明的流程入参，任何节点都可引用。 */
    private List<String> startInputs() {
        for (FlowNodeDefinition node : graph.getNodes()) {
            boolean isStart = "START".equalsIgnoreCase(node.getNodeType())
                    || "ENTRY".equalsIgnoreCase(node.getStateType());
            if (!isStart || node.getNodeConfig() == null) {
                continue;
            }
            Object inputs = node.getNodeConfig().get("inputs");
            if (inputs instanceof Map<?, ?> map) {
                return map.keySet().stream().map(String::valueOf).toList();
            }
            if (inputs instanceof List<?> list) {
                List<String> keys = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> spec && spec.get("key") != null) {
                        keys.add(String.valueOf(spec.get("key")));
                    } else if (item != null) {
                        keys.add(String.valueOf(item));
                    }
                }
                return keys;
            }
        }
        return List.of();
    }

    /** 目标节点当前模板里已经引用的变量，便于模型自查是否有笔误。 */
    private List<String> referencedVariables(FlowNodeDefinition node) {
        Set<String> found = new LinkedHashSet<>();
        collectVariables(node.getPromptTemplate(), found);
        collectVariables(node.getSystemPrompt(), found);
        return List.copyOf(found);
    }

    private void collectVariables(String template, Set<String> sink) {
        if (template == null || template.isBlank()) {
            return;
        }
        Matcher matcher = VARIABLE.matcher(template);
        while (matcher.find()) {
            sink.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
    }

    private boolean isProducer(FlowNodeDefinition node) {
        return node.getNodeType() != null && PRODUCER_TYPES.contains(node.getNodeType().toUpperCase());
    }

    private String effectiveOutputKey(FlowNodeDefinition node) {
        String outputKey = node.getOutputKey();
        return outputKey == null || outputKey.isBlank() ? node.getNodeCode() : outputKey;
    }

    private Set<String> upstreamOf(String nodeCode) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(predecessors.getOrDefault(nodeCode, Set.of()));
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (result.add(current)) {
                queue.addAll(predecessors.getOrDefault(current, Set.of()));
            }
        }
        return result;
    }

    /**
     * 目标节点的支配集：从入口出发、**删除该节点后目标不可达**的节点集合。
     *
     * <p>用「删点后可达性」定义支配，避免实现完整的支配树算法；草稿规模有节点数上限，
     * 这里的 O(N·(N+E)) 完全可接受，且语义直观、不易写错。
     */
    private Set<String> dominatorsOf(String targetCode) {
        String entry = entryNode();
        Set<String> dominators = new LinkedHashSet<>();
        if (entry == null || entry.equals(targetCode)) {
            return dominators;
        }
        for (String candidate : upstreamOf(targetCode)) {
            if (candidate.equals(entry)) {
                dominators.add(candidate);
                continue;
            }
            if (!reachable(entry, targetCode, candidate)) {
                dominators.add(candidate);
            }
        }
        return dominators;
    }

    /** entry 能否在绕开 blocked 的情况下到达 target。 */
    private boolean reachable(String entry, String target, String blocked) {
        Map<String, Set<String>> successors = new HashMap<>();
        for (FlowEdgeDefinition edge : graph.getEdges()) {
            successors.computeIfAbsent(edge.getFromNode(), key -> new HashSet<>()).add(edge.getToNode());
        }
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (current.equals(blocked) || !seen.add(current)) {
                continue;
            }
            if (current.equals(target)) {
                return true;
            }
            queue.addAll(successors.getOrDefault(current, Set.of()));
        }
        return false;
    }

    private String entryNode() {
        for (FlowNodeDefinition node : graph.getNodes()) {
            if ("START".equalsIgnoreCase(node.getNodeType()) || "ENTRY".equalsIgnoreCase(node.getStateType())) {
                return node.getNodeCode();
            }
        }
        // 无显式入口时退化为「无入边的唯一节点」
        List<String> roots = graph.getNodes().stream()
                .map(FlowNodeDefinition::getNodeCode)
                .filter(code -> predecessors.getOrDefault(code, Set.of()).isEmpty())
                .toList();
        return roots.size() == 1 ? roots.get(0) : null;
    }
}
