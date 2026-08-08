package com.nebula.common.ai.harness.validate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.draft.DraftIssue;
import com.nebula.common.ai.harness.draft.EngineTypeCatalog;

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
 * 两种流程引擎共享的结构与模板数据流规则。
 *
 * <p>模板变量按 {@code inputMapping} 映射为上下文键。只有入口输入或支配当前节点的上游产物才视为
 * 必然可用；仅在部分路径产生的键返回 WARN，完全没有来源时返回 ERROR。
 *
 * @author nebula
 */
public class CommonRules {

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile(
            "\\{\\{\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*}}|#\\{\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*}");

    public List<DraftIssue> validate(String engineType, FlowDefinition definition) {
        List<DraftIssue> issues = new ArrayList<>();
        List<FlowNodeDefinition> nodes = nodes(definition);
        List<FlowEdgeDefinition> edges = edges(definition);
        Map<String, FlowNodeDefinition> nodeByCode = new LinkedHashMap<>();

        for (FlowNodeDefinition node : nodes) {
            String nodeCode = node.getNodeCode();
            if (hasText(nodeCode) && nodeByCode.putIfAbsent(nodeCode, node) != null) {
                issues.add(error("DUPLICATE_NODE_CODE", nodeCode, "nodeCode",
                        "nodeCode 在草稿内必须唯一", "修改或删除重复节点后重新校验"));
            }
        }
        for (FlowEdgeDefinition edge : edges) {
            if (!nodeByCode.containsKey(edge.getFromNode()) || !nodeByCode.containsKey(edge.getToNode())) {
                issues.add(error("EDGE_ENDPOINT_NOT_FOUND", edge.getFromNode(), "edges",
                        "边端点不存在: " + edge.getFromNode() + " -> " + edge.getToNode(),
                        "删除错误边或先补齐端点节点"));
            }
        }

        if (nodeByCode.size() != nodes.size()) {
            return issues;
        }
        String entry = findEntry(engineType, nodes);
        if (entry == null) {
            return issues;
        }
        issues.addAll(validateTemplateVariables(definition, nodeByCode, edges, entry));
        return issues;
    }

    private List<DraftIssue> validateTemplateVariables(FlowDefinition definition,
                                                       Map<String, FlowNodeDefinition> nodeByCode,
                                                       List<FlowEdgeDefinition> edges,
                                                       String entry) {
        List<DraftIssue> issues = new ArrayList<>();
        Set<String> reachable = reachableFrom(entry, edges);
        Map<String, Set<String>> predecessors = predecessors(edges, nodeByCode.keySet());
        Map<String, Set<String>> dominators = dominators(entry, reachable, predecessors);
        Set<String> initialKeys = inputKeys(nodeByCode.get(entry));

        for (FlowNodeDefinition node : nodeByCode.values()) {
            if (!reachable.contains(node.getNodeCode())) {
                continue;
            }
            Set<String> variables = new LinkedHashSet<>();
            variables.addAll(templateVariables(node.getSystemPrompt()));
            variables.addAll(templateVariables(node.getPromptTemplate()));
            for (String variable : variables) {
                String contextKey = node.getInputMapping() == null
                        ? variable : node.getInputMapping().getOrDefault(variable, variable);
                if (initialKeys.contains(contextKey)
                        || producedByAny(dominators.getOrDefault(node.getNodeCode(), Set.of()),
                        nodeByCode, node.getNodeCode(), contextKey)) {
                    continue;
                }
                if (producedByAny(upstreamOf(node.getNodeCode(), predecessors),
                        nodeByCode, node.getNodeCode(), contextKey)) {
                    issues.add(DraftIssue.warn("TEMPLATE_VARIABLE_NOT_DOMINATED", node.getNodeCode(),
                            templateField(node, variable),
                            "模板变量 " + variable + " 映射到上下文键 " + contextKey
                                    + "，但该键只在部分上游路径产生",
                            "调整连边保证产物支配当前节点，或为缺失路径提供默认值"));
                } else {
                    issues.add(error("UNRESOLVED_TEMPLATE_VARIABLE", node.getNodeCode(),
                            templateField(node, variable),
                            "模板变量 " + variable + " 映射到上下文键 " + contextKey + "，但没有可用来源",
                            "在入口 inputs 声明该键，或让上游节点通过 outputKey 产出"));
                }
            }
        }
        return issues;
    }

    private Map<String, Set<String>> dominators(String entry,
                                                Set<String> reachable,
                                                Map<String, Set<String>> predecessors) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String code : reachable) {
            result.put(code, code.equals(entry) ? new HashSet<>(Set.of(entry)) : new HashSet<>(reachable));
        }
        boolean changed;
        do {
            changed = false;
            for (String code : reachable) {
                if (code.equals(entry)) {
                    continue;
                }
                List<Set<String>> incoming = predecessors.getOrDefault(code, Set.of()).stream()
                        .filter(reachable::contains)
                        .map(result::get)
                        .toList();
                Set<String> next = new HashSet<>();
                if (!incoming.isEmpty()) {
                    next.addAll(incoming.getFirst());
                    incoming.stream().skip(1).forEach(next::retainAll);
                }
                next.add(code);
                if (!next.equals(result.get(code))) {
                    result.put(code, next);
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private Set<String> upstreamOf(String nodeCode, Map<String, Set<String>> predecessors) {
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(predecessors.getOrDefault(nodeCode, Set.of()));
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (result.add(current)) {
                queue.addAll(predecessors.getOrDefault(current, Set.of()));
            }
        }
        return result;
    }

    private boolean producedByAny(Set<String> candidates,
                                  Map<String, FlowNodeDefinition> nodeByCode,
                                  String currentNode,
                                  String contextKey) {
        for (String code : candidates) {
            if (code.equals(currentNode)) {
                continue;
            }
            FlowNodeDefinition producer = nodeByCode.get(code);
            if (producer != null && contextKey.equals(producer.getOutputKey())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> inputKeys(FlowNodeDefinition entry) {
        if (entry == null || entry.getNodeConfig() == null) {
            return Set.of();
        }
        Object inputs = entry.getNodeConfig().get("inputs");
        Set<String> keys = new LinkedHashSet<>();
        if (inputs instanceof Map<?, ?> map) {
            map.keySet().stream().filter(key -> key != null).map(String::valueOf).forEach(keys::add);
        } else if (inputs instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    Object key = row.get("key") == null ? row.get("name") : row.get("key");
                    if (key != null && !String.valueOf(key).isBlank()) {
                        keys.add(String.valueOf(key).trim());
                    }
                }
            }
        }
        return keys;
    }

    private Map<String, Set<String>> predecessors(List<FlowEdgeDefinition> edges, Set<String> nodeCodes) {
        Map<String, Set<String>> result = new HashMap<>();
        nodeCodes.forEach(code -> result.put(code, new LinkedHashSet<>()));
        for (FlowEdgeDefinition edge : edges) {
            if (nodeCodes.contains(edge.getFromNode()) && nodeCodes.contains(edge.getToNode())) {
                result.get(edge.getToNode()).add(edge.getFromNode());
            }
        }
        return result;
    }

    private Set<String> reachableFrom(String start, List<FlowEdgeDefinition> edges) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        result.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (FlowEdgeDefinition edge : edges) {
                if (current.equals(edge.getFromNode()) && result.add(edge.getToNode())) {
                    queue.addLast(edge.getToNode());
                }
            }
        }
        return result;
    }

    private Set<String> templateVariables(String template) {
        Set<String> result = new LinkedHashSet<>();
        if (!hasText(template)) {
            return result;
        }
        Matcher matcher = TEMPLATE_VARIABLE.matcher(template);
        while (matcher.find()) {
            result.add(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
        }
        return result;
    }

    private String templateField(FlowNodeDefinition node, String variable) {
        return templateVariables(node.getPromptTemplate()).contains(variable) ? "promptTemplate" : "systemPrompt";
    }

    private String findEntry(String engineType, List<FlowNodeDefinition> nodes) {
        List<FlowNodeDefinition> entries = nodes.stream()
                .filter(node -> EngineTypeCatalog.STATE_MACHINE.equals(engineType)
                        ? "ENTRY".equals(node.getStateType()) : "START".equals(node.getNodeType()))
                .toList();
        return entries.size() == 1 ? entries.getFirst().getNodeCode() : null;
    }

    private List<FlowNodeDefinition> nodes(FlowDefinition definition) {
        return definition == null || definition.getNodes() == null ? List.of() : definition.getNodes();
    }

    private List<FlowEdgeDefinition> edges(FlowDefinition definition) {
        return definition == null || definition.getEdges() == null ? List.of() : definition.getEdges();
    }

    private DraftIssue error(String code, String nodeCode, String field, String message, String hint) {
        return DraftIssue.error(code, nodeCode, field, message, hint);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
