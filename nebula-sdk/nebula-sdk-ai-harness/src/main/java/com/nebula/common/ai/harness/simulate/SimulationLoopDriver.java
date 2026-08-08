package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.draft.DraftIssue;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** LOOP 的专用有界驱动器，不复用生产 LoopNodeExecutor。 */
public class SimulationLoopDriver {

    private final HarnessSimulationProperties properties;
    private final SimulationExecutorRegistry registry;

    public SimulationLoopDriver(HarnessSimulationProperties properties, SimulationExecutorRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    public List<DraftIssue> execute(FlowDefinition definition,
                                    FlowNodeDefinition loop,
                                    SimulationContext context,
                                    Consumer<String> reachedNode) {
        return execute(definition, loop, context, reachedNode, new LinkedHashSet<>());
    }

    private List<DraftIssue> execute(FlowDefinition definition,
                                     FlowNodeDefinition loop,
                                     SimulationContext context,
                                     Consumer<String> reachedNode,
                                     Set<String> activeLoops) {
        if (!activeLoops.add(loop.getNodeCode())) {
            return List.of(DraftIssue.error("SIMULATION_LOOP_NESTING_CYCLE", loop.getNodeCode(),
                    "nodeConfig.members", "LOOP members 存在循环引用", "移除 LOOP 之间的循环成员关系"));
        }
        Set<String> members = members(loop);
        Map<String, FlowNodeDefinition> nodes = definition.getNodes().stream()
                .collect(Collectors.toMap(FlowNodeDefinition::getNodeCode, node -> node));
        List<FlowNodeDefinition> ordered = members.stream().map(nodes::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(FlowNodeDefinition::getSortNo))
                .toList();
        if (ordered.isEmpty()) {
            activeLoops.remove(loop.getNodeCode());
            return List.of(DraftIssue.error("SIMULATION_LOOP_EMPTY", loop.getNodeCode(), "nodeConfig.members",
                    "LOOP 没有可模拟的成员", "补齐 members 后重新模拟"));
        }
        for (FlowNodeDefinition member : ordered) {
            if (!registry.supports(member.getNodeType()) && !"LOOP".equalsIgnoreCase(member.getNodeType())) {
                activeLoops.remove(loop.getNodeCode());
                return List.of(missingExecutor(member));
            }
        }
        for (int iteration = 0; iteration < Math.max(1, properties.getLoopIterations()); iteration++) {
            for (FlowNodeDefinition member : ordered) {
                reachedNode.accept(member.getNodeCode());
                List<DraftIssue> unresolved = SimulationSupport.unresolvedInputs(member, context);
                if (!unresolved.isEmpty()) {
                    activeLoops.remove(loop.getNodeCode());
                    return unresolved;
                }
                if ("LOOP".equalsIgnoreCase(member.getNodeType())) {
                    List<DraftIssue> nested = execute(definition, member, context, reachedNode, activeLoops);
                    if (!nested.isEmpty()) {
                        activeLoops.remove(loop.getNodeCode());
                        return nested;
                    }
                } else {
                    registry.execute(member, context);
                }
            }
        }
        if (loop.getOutputKey() != null && !loop.getOutputKey().isBlank()) {
            context.putSynthetic(loop.getOutputKey(), "[模拟循环输出:" + loop.getNodeCode() + "]");
        }
        activeLoops.remove(loop.getNodeCode());
        return List.of();
    }

    private Set<String> members(FlowNodeDefinition node) {
        Set<String> result = new LinkedHashSet<>();
        Object raw = node.getNodeConfig() == null ? null : node.getNodeConfig().get("members");
        if (raw instanceof List<?> list) {
            list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).forEach(result::add);
        }
        return result;
    }

    private DraftIssue missingExecutor(FlowNodeDefinition node) {
        return DraftIssue.error("SIMULATION_EXECUTOR_NOT_FOUND", node.getNodeCode(), "nodeType",
                "未注册节点类型的模拟执行器: " + node.getNodeType(),
                "为该 nodeType 提供 SimulationNodeExecutor SPI 实现");
    }
}
