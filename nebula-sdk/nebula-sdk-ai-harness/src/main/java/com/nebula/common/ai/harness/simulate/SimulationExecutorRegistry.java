package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowNodeDefinition;

import java.util.List;

/** 按 nodeType 选择模拟替身，不允许回退到生产执行器。 */
public class SimulationExecutorRegistry {

    private final List<SimulationNodeExecutor> executors;

    public SimulationExecutorRegistry(List<SimulationNodeExecutor> executors) {
        this.executors = executors == null ? List.of() : List.copyOf(executors);
    }

    public boolean supports(String nodeType) {
        return executors.stream().anyMatch(executor -> executor.supports(nodeType));
    }

    public void execute(FlowNodeDefinition node, SimulationContext context) {
        SimulationNodeExecutor executor = executors.stream()
                .filter(candidate -> candidate.supports(node.getNodeType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未注册节点模拟器: " + node.getNodeType()));
        executor.execute(node, context);
    }
}
