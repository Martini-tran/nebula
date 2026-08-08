package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowNodeDefinition;

import java.util.Set;

/** START/END/IF/JOIN 等纯结构节点的无操作替身。 */
public class StructuralSimulationNodeExecutor implements SimulationNodeExecutor {

    private static final Set<String> TYPES = Set.of("START", "END", "IF", "JOIN");

    @Override
    public boolean supports(String nodeType) {
        return nodeType != null && TYPES.contains(nodeType.toUpperCase());
    }

    @Override
    public void execute(FlowNodeDefinition node, SimulationContext context) {
        // 结构节点只负责推进图，不产生业务副作用。
    }
}
