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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 状态机的角色、终态可达性与默认转移规则。 */
public class StateMachineRuleSet implements EngineRuleSet {

    private static final Set<String> STATE_TYPES = Set.of("ENTRY", "NORMAL", "TERMINAL");

    @Override
    public String engineType() {
        return EngineTypeCatalog.STATE_MACHINE;
    }

    @Override
    public List<DraftIssue> validate(FlowDefinition definition) {
        List<DraftIssue> issues = new ArrayList<>();
        List<FlowNodeDefinition> nodes = definition.getNodes() == null ? List.of() : definition.getNodes();
        List<FlowEdgeDefinition> edges = definition.getEdges() == null ? List.of() : definition.getEdges();
        List<String> entries = new ArrayList<>();
        Set<String> terminals = new HashSet<>();
        Map<String, List<FlowEdgeDefinition>> outgoing = new HashMap<>();
        edges.forEach(edge -> outgoing.computeIfAbsent(edge.getFromNode(), key -> new ArrayList<>()).add(edge));

        for (FlowNodeDefinition node : nodes) {
            if (!STATE_TYPES.contains(node.getStateType())) {
                issues.add(DraftIssue.error("INVALID_STATE_TYPE", node.getNodeCode(), "stateType",
                        "stateType 必须严格为 ENTRY、NORMAL 或 TERMINAL", "修正 stateType 后重新校验"));
            }
            if ("ENTRY".equals(node.getStateType())) {
                entries.add(node.getNodeCode());
            }
            if ("TERMINAL".equals(node.getStateType())) {
                terminals.add(node.getNodeCode());
                if (!outgoing.getOrDefault(node.getNodeCode(), List.of()).isEmpty()) {
                    issues.add(DraftIssue.error("TERMINAL_HAS_OUTGOING_EDGE", node.getNodeCode(), "edges",
                            "TERMINAL 状态不能存在出边", "删除终态出边或把该节点改为 NORMAL"));
                }
            }
        }
        if (entries.size() != 1) {
            issues.add(error("INVALID_ENTRY_COUNT", "状态机必须恰好包含一个 ENTRY，当前为 " + entries.size(),
                    "添加缺失的 ENTRY，或把多余 ENTRY 改为 NORMAL"));
        }
        if (terminals.isEmpty()) {
            issues.add(error("MISSING_TERMINAL", "状态机至少需要一个 TERMINAL", "添加可正常结束的终态"));
        }

        if (entries.size() == 1) {
            Set<String> reachable = reachable(entries.getFirst(), outgoing);
            if (!terminals.isEmpty() && terminals.stream().noneMatch(reachable::contains)) {
                issues.add(error("TERMINAL_UNREACHABLE", "从 ENTRY 无法到达任何 TERMINAL",
                        "补充一条从入口路径通向终态的转移"));
            }
            for (FlowNodeDefinition node : nodes) {
                if (!reachable.contains(node.getNodeCode())) {
                    issues.add(DraftIssue.warn("UNREACHABLE_STATE", node.getNodeCode(), "edges",
                            "状态从 ENTRY 不可达，将不会执行", "接入状态机主路径或删除该状态"));
                }
            }
        }
        for (Map.Entry<String, List<FlowEdgeDefinition>> entry : outgoing.entrySet()) {
            if (!entry.getValue().isEmpty()
                    && entry.getValue().stream().allMatch(edge -> hasText(edge.getConditionExpr()))) {
                issues.add(DraftIssue.warn("MISSING_DEFAULT_TRANSITION", entry.getKey(), "conditionExpr",
                        "该状态全部出边都带 guard，全部为 false 时状态机会卡住",
                        "保留一条无 conditionExpr 的低优先级默认边"));
            }
        }
        return issues;
    }

    private Set<String> reachable(String entry, Map<String, List<FlowEdgeDefinition>> outgoing) {
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        result.add(entry);
        queue.add(entry);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (FlowEdgeDefinition edge : outgoing.getOrDefault(current, List.of())) {
                if (result.add(edge.getToNode())) {
                    queue.addLast(edge.getToNode());
                }
            }
        }
        return result;
    }

    private DraftIssue error(String code, String message, String hint) {
        return DraftIssue.error(code, null, "nodes", message, hint);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
