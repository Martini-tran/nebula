package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.draft.DraftIssue;
import com.nebula.common.ai.harness.draft.EngineTypeCatalog;
import com.nebula.common.ai.harness.simulate.SimulationConditionEvaluator.ConditionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 状态机的有界单路径模拟器，按运行时 sortNo 裁决顺序推进。 */
public class StateMachineSimulator implements EngineSimulator {

    private final HarnessSimulationProperties properties;
    private final SimulationExecutorRegistry registry;
    private final SimulationConditionEvaluator conditionEvaluator;

    public StateMachineSimulator(HarnessSimulationProperties properties,
                                 SimulationExecutorRegistry registry,
                                 SimulationConditionEvaluator conditionEvaluator) {
        this.properties = properties;
        this.registry = registry;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public String engineType() {
        return EngineTypeCatalog.STATE_MACHINE;
    }

    @Override
    public SimulationReport simulate(FlowDefinition definition, Map<String, Object> initialInput) {
        Map<String, FlowNodeDefinition> nodes = definition.getNodes().stream()
                .collect(Collectors.toMap(FlowNodeDefinition::getNodeCode, node -> node));
        FlowNodeDefinition entry = definition.getNodes().stream()
                .filter(node -> "ENTRY".equals(node.getStateType())).findFirst().orElse(null);
        if (entry == null) {
            return failure("SIMULATION_ENTRY_MISSING", "状态机模拟找不到 ENTRY");
        }
        Map<String, List<FlowEdgeDefinition>> outgoing = new LinkedHashMap<>();
        for (FlowEdgeDefinition edge : definition.getEdges()) {
            outgoing.computeIfAbsent(edge.getFromNode(), key -> new ArrayList<>()).add(edge);
        }
        outgoing.values().forEach(edges -> edges.sort(Comparator.comparingInt(FlowEdgeDefinition::getSortNo)));

        SimulationContext context = new SimulationContext(initialInput);
        SimulationSupport.applyEntryDefaults(entry, context);
        List<String> path = new ArrayList<>();
        List<Map<String, Object>> undecidedGuards = new ArrayList<>();
        Set<String> fingerprints = new LinkedHashSet<>();
        Set<DraftIssue> issues = new LinkedHashSet<>();
        String current = entry.getNodeCode();
        String terminal = null;
        boolean loopDetected = false;
        int transitions = 0;
        int limit = Math.min(Math.max(1, definition.getMaxTransitions()),
                Math.max(1, properties.getMaxStateTransitions()));

        while (transitions <= limit) {
            FlowNodeDefinition node = nodes.get(current);
            if (node == null) {
                issues.add(DraftIssue.error("SIMULATION_NODE_MISSING", current, "edges",
                        "模拟路径引用了不存在的状态", "修正转移端点后重新模拟"));
                break;
            }
            String fingerprint = current + "|" + String.join(",", context.keys().stream().sorted().toList());
            if (!fingerprints.add(fingerprint)) {
                loopDetected = true;
                issues.add(DraftIssue.error("SIMULATION_LOOP", current, "edges",
                        "状态与上下文键集合重复，模拟路径没有进展", "调整 guard、产物或转移顺序以确保能够退出循环"));
                break;
            }

            path.add(current);
            issues.addAll(SimulationSupport.unresolvedInputs(node, context));
            if (registry.supports(node.getNodeType())) {
                registry.execute(node, context);
            } else {
                issues.add(DraftIssue.error("SIMULATION_EXECUTOR_NOT_FOUND", node.getNodeCode(), "nodeType",
                        "未注册节点类型的模拟执行器: " + node.getNodeType(),
                        "为该 nodeType 提供 SimulationNodeExecutor SPI 实现"));
                break;
            }
            if ("TERMINAL".equals(node.getStateType())) {
                terminal = current;
                break;
            }
            if (transitions == limit) {
                issues.add(DraftIssue.error("SIMULATION_TRANSITION_LIMIT", current, "maxTransitions",
                        "状态机模拟达到转移上限 " + limit, "调整状态转移或提高受控上限"));
                break;
            }

            FlowEdgeDefinition selected = null;
            for (FlowEdgeDefinition edge : outgoing.getOrDefault(current, List.of())) {
                ConditionResult result = conditionEvaluator.evaluate(edge.getConditionExpr(), context);
                if (result == ConditionResult.TRUE) {
                    selected = edge;
                    break;
                }
                if (result == ConditionResult.UNDECIDED) {
                    selected = edge;
                    Map<String, Object> assumption = new LinkedHashMap<>();
                    assumption.put("from", edge.getFromNode());
                    assumption.put("chosen", edge.getToNode());
                    assumption.put("sortNo", edge.getSortNo());
                    assumption.put("reason", "guard 无法由已知模拟上下文判定，按 sortNo 假设首条可满足");
                    undecidedGuards.add(assumption);
                    issues.add(DraftIssue.warn("SIMULATION_GUARD_ASSUMED", current, "conditionExpr",
                            "guard 无法判定，模拟已假设转移到 " + edge.getToNode(),
                            "提供能够判定 guard 的 initialInput，或以真实试跑确认"));
                    break;
                }
            }
            if (selected == null) {
                issues.add(DraftIssue.error("SIMULATION_DEAD_END", current, "edges",
                        "当前状态没有可满足的转移且不是 TERMINAL", "补充 default 转移或修正 guard"));
                break;
            }
            current = selected.getToNode();
            transitions++;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transitionPath", List.copyOf(path));
        details.put("reachedTerminal", terminal != null);
        if (terminal != null) {
            details.put("terminalState", terminal);
        }
        details.put("transitionCount", transitions);
        details.put("loopDetected", loopDetected);
        details.put("undecidedGuards", List.copyOf(undecidedGuards));
        details.put("contextKeys", context.keys().stream().sorted().toList());
        return new SimulationReport(engineType(), undecidedGuards.isEmpty()
                ? SimulationConfidence.CONFIRMED : SimulationConfidence.ASSUMED,
                List.copyOf(issues), details);
    }

    private SimulationReport failure(String code, String message) {
        return new SimulationReport(engineType(), SimulationConfidence.CONFIRMED,
                List.of(DraftIssue.error(code, null, "nodes", message, "先修正校验错误")), Map.of());
    }
}
