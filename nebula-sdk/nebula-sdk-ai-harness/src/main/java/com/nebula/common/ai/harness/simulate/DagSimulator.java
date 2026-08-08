package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.draft.DraftIssue;
import com.nebula.common.ai.harness.draft.EngineTypeCatalog;
import com.nebula.common.ai.harness.simulate.SimulationConditionEvaluator.ConditionResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** DAG 的有界分支模拟器。 */
public class DagSimulator implements EngineSimulator {

    private final HarnessSimulationProperties properties;
    private final SimulationExecutorRegistry registry;
    private final SimulationLoopDriver loopDriver;
    private final SimulationConditionEvaluator conditionEvaluator;

    public DagSimulator(HarnessSimulationProperties properties,
                        SimulationExecutorRegistry registry,
                        SimulationLoopDriver loopDriver,
                        SimulationConditionEvaluator conditionEvaluator) {
        this.properties = properties;
        this.registry = registry;
        this.loopDriver = loopDriver;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public String engineType() {
        return EngineTypeCatalog.DAG;
    }

    @Override
    public SimulationReport simulate(FlowDefinition definition, Map<String, Object> initialInput) {
        Map<String, FlowNodeDefinition> nodes = definition.getNodes().stream()
                .collect(Collectors.toMap(FlowNodeDefinition::getNodeCode, node -> node));
        FlowNodeDefinition start = definition.getNodes().stream()
                .filter(node -> "START".equals(node.getNodeType())).findFirst().orElse(null);
        if (start == null) {
            return failure("SIMULATION_START_MISSING", "DAG 模拟找不到 START 节点");
        }

        Map<String, String> memberOwners = memberOwners(definition.getNodes());
        Map<String, List<EffectiveEdge>> outgoing = effectiveOutgoing(definition.getEdges(), memberOwners);
        Deque<PathState> queue = new ArrayDeque<>();
        queue.add(new PathState(start.getNodeCode(), new SimulationContext(initialInput)));
        Set<String> reached = new LinkedHashSet<>();
        Set<String> contextKeys = new LinkedHashSet<>();
        Set<DraftIssue> issues = new LinkedHashSet<>();
        List<Map<String, Object>> undecidedBranches = new ArrayList<>();
        int exploredPaths = 1;
        boolean assumed = false;
        boolean truncated = false;

        while (!queue.isEmpty()) {
            PathState path = queue.removeFirst();
            FlowNodeDefinition node = nodes.get(path.nodeCode());
            if (node == null) {
                issues.add(DraftIssue.error("SIMULATION_NODE_MISSING", path.nodeCode(), "edges",
                        "模拟路径引用了不存在的节点", "修正边端点后重新模拟"));
                continue;
            }
            reached.add(node.getNodeCode());
            if ("START".equals(node.getNodeType())) {
                SimulationSupport.applyEntryDefaults(node, path.context());
            }
            issues.addAll(SimulationSupport.unresolvedInputs(node, path.context()));
            if ("LOOP".equalsIgnoreCase(node.getNodeType())) {
                issues.addAll(loopDriver.execute(definition, node, path.context(), reached::add));
            } else if (registry.supports(node.getNodeType())) {
                registry.execute(node, path.context());
            } else {
                issues.add(missingExecutor(node));
            }
            contextKeys.addAll(path.context().keys());

            List<EffectiveEdge> candidates = new ArrayList<>();
            int undecidedCount = 0;
            for (EffectiveEdge edge : outgoing.getOrDefault(node.getNodeCode(), List.of())) {
                ConditionResult result = conditionEvaluator.evaluate(edge.conditionExpr(), path.context());
                if (result == ConditionResult.TRUE) {
                    candidates.add(edge);
                } else if (result == ConditionResult.UNDECIDED) {
                    if (undecidedCount++ < properties.getMaxBranchCandidates()) {
                        candidates.add(edge);
                        assumed = true;
                        Map<String, Object> assumption = new LinkedHashMap<>();
                        assumption.put("from", node.getNodeCode());
                        assumption.put("candidate", edge.toNode());
                        assumption.put("sortNo", edge.sortNo());
                        assumption.put("reason", "conditionExpr 无法由已知模拟上下文判定，已纳入有界分支探索");
                        undecidedBranches.add(assumption);
                        issues.add(DraftIssue.warn("SIMULATION_GUARD_ASSUMED", node.getNodeCode(),
                                "conditionExpr", "条件无法判定，模拟已探索候选分支 " + edge.toNode(),
                                "提供能够判定条件的 initialInput，或以真实试跑确认"));
                    } else {
                        truncated = true;
                    }
                }
            }
            if (candidates.isEmpty() && !"END".equals(node.getNodeType())) {
                issues.add(DraftIssue.error("SIMULATION_DEAD_END", node.getNodeCode(), "edges",
                        "当前 DAG 路径没有可满足的出边且尚未到达 END",
                        "补充 default 边、修正条件或提供能够满足条件的 initialInput"));
            }
            int accepted = 0;
            for (EffectiveEdge edge : candidates) {
                int additionalPath = accepted == 0 ? 0 : 1;
                if (exploredPaths + additionalPath > properties.getMaxDagPaths()) {
                    truncated = true;
                    break;
                }
                queue.addLast(new PathState(edge.toNode(), path.context().copy()));
                exploredPaths += additionalPath;
                accepted++;
            }
        }

        if (truncated) {
            issues.add(DraftIssue.warn("SIMULATION_COVERAGE_TRUNCATED", null, "edges",
                    "DAG 分支探索达到路径预算 " + properties.getMaxDagPaths(),
                    "补充可判定的 initialInput，或收敛分支数量后重试"));
        }
        Set<String> unreachable = definition.getNodes().stream().map(FlowNodeDefinition::getNodeCode)
                .filter(code -> !reached.contains(code)).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reachedNodes", List.copyOf(reached));
        details.put("unreachableNodes", List.copyOf(unreachable));
        details.put("contextKeys", List.copyOf(contextKeys));
        details.put("exploredPaths", exploredPaths);
        details.put("coverageTruncated", truncated);
        details.put("undecidedBranches", List.copyOf(undecidedBranches));
        return new SimulationReport(engineType(), assumed ? SimulationConfidence.ASSUMED
                : SimulationConfidence.CONFIRMED, List.copyOf(issues), details);
    }

    private Map<String, String> memberOwners(List<FlowNodeDefinition> nodes) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (FlowNodeDefinition node : nodes) {
            Object raw = node.getNodeConfig() == null ? null : node.getNodeConfig().get("members");
            if ("LOOP".equalsIgnoreCase(node.getNodeType()) && raw instanceof List<?> members) {
                members.stream().filter(java.util.Objects::nonNull)
                        .forEach(member -> owners.put(String.valueOf(member), node.getNodeCode()));
            }
        }
        return owners;
    }

    private Map<String, List<EffectiveEdge>> effectiveOutgoing(List<FlowEdgeDefinition> edges,
                                                                Map<String, String> memberOwners) {
        Map<String, List<EffectiveEdge>> result = new LinkedHashMap<>();
        for (FlowEdgeDefinition edge : edges) {
            String from = memberOwners.getOrDefault(edge.getFromNode(), edge.getFromNode());
            String to = memberOwners.getOrDefault(edge.getToNode(), edge.getToNode());
            if (!from.equals(to)) {
                result.computeIfAbsent(from, key -> new ArrayList<>())
                        .add(new EffectiveEdge(to, edge.getConditionExpr(), edge.getSortNo()));
            }
        }
        result.values().forEach(list -> list.sort(Comparator.comparingInt(EffectiveEdge::sortNo)));
        return result;
    }

    private SimulationReport failure(String code, String message) {
        return new SimulationReport(engineType(), SimulationConfidence.CONFIRMED,
                List.of(DraftIssue.error(code, null, "nodes", message, "先修正校验错误")), Map.of());
    }

    private DraftIssue missingExecutor(FlowNodeDefinition node) {
        return DraftIssue.error("SIMULATION_EXECUTOR_NOT_FOUND", node.getNodeCode(), "nodeType",
                "未注册节点类型的模拟执行器: " + node.getNodeType(),
                "为该 nodeType 提供 SimulationNodeExecutor SPI 实现");
    }

    private record PathState(String nodeCode, SimulationContext context) {
    }

    private record EffectiveEdge(String toNode, String conditionExpr, int sortNo) {
    }
}
