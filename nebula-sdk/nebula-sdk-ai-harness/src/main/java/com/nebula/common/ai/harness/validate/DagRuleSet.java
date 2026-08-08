package com.nebula.common.ai.harness.validate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.draft.DraftIssue;
import com.nebula.common.ai.harness.draft.EngineTypeCatalog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** DAG 的入口、出口与可达性规则。 */
public class DagRuleSet implements EngineRuleSet {

    @Override
    public String engineType() {
        return EngineTypeCatalog.DAG;
    }

    @Override
    public List<DraftIssue> validate(FlowDefinition definition) {
        List<DraftIssue> issues = new ArrayList<>();
        List<FlowNodeDefinition> nodes = definition.getNodes() == null ? List.of() : definition.getNodes();
        List<FlowEdgeDefinition> edges = definition.getEdges() == null ? List.of() : definition.getEdges();
        List<String> starts = nodeCodes(nodes, "START");
        List<String> ends = nodeCodes(nodes, "END");

        if (starts.size() != 1) {
            issues.add(error("INVALID_START_COUNT", "DAG 必须恰好包含一个 START 节点，当前为 " + starts.size(),
                    "添加缺失的 START，或删除多余 START"));
        }
        if (ends.size() != 1) {
            issues.add(error("INVALID_END_COUNT", "DAG 必须恰好包含一个 END 节点，当前为 " + ends.size(),
                    "添加缺失的 END，或删除多余 END"));
        }

        Set<String> connected = new HashSet<>();
        edges.forEach(edge -> {
            connected.add(edge.getFromNode());
            connected.add(edge.getToNode());
        });
        for (FlowNodeDefinition node : nodes) {
            if (!connected.contains(node.getNodeCode()) && nodes.size() > 1) {
                issues.add(DraftIssue.warn("ORPHAN_NODE", node.getNodeCode(), "edges",
                        "节点没有任何入边或出边", "将节点接入主流程，或删除无用节点"));
            }
        }

        if (starts.size() == 1) {
            Set<String> fromStart = traverse(starts.getFirst(), edges, false);
            for (FlowNodeDefinition node : nodes) {
                if (!fromStart.contains(node.getNodeCode())) {
                    issues.add(error("NODE_UNREACHABLE_FROM_START", node.getNodeCode(),
                            "节点无法从 START 到达", "补充从主流程到该节点的连边"));
                }
            }
        }
        if (ends.size() == 1) {
            Set<String> toEnd = traverse(ends.getFirst(), edges, true);
            for (FlowNodeDefinition node : nodes) {
                if (!toEnd.contains(node.getNodeCode())) {
                    issues.add(error("NODE_CANNOT_REACH_END", node.getNodeCode(),
                            "节点不存在到 END 的路径", "补充通向 END 的连边"));
                }
            }
        }
        return issues;
    }

    private List<String> nodeCodes(List<FlowNodeDefinition> nodes, String type) {
        return nodes.stream().filter(node -> type.equals(node.getNodeType()))
                .map(FlowNodeDefinition::getNodeCode).toList();
    }

    private Set<String> traverse(String start, List<FlowEdgeDefinition> edges, boolean reverse) {
        Set<String> reached = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        reached.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (FlowEdgeDefinition edge : edges) {
                String from = reverse ? edge.getToNode() : edge.getFromNode();
                String to = reverse ? edge.getFromNode() : edge.getToNode();
                if (current.equals(from) && reached.add(to)) {
                    queue.addLast(to);
                }
            }
        }
        return reached;
    }

    private DraftIssue error(String code, String message, String hint) {
        return error(code, null, message, hint);
    }

    private DraftIssue error(String code, String nodeCode, String message, String hint) {
        return DraftIssue.error(code, nodeCode, "nodes", message, hint);
    }
}
