package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowNodeDefinition;

import java.util.Map;
import java.util.Set;

/** PROMPT/TOOL/AGENT 的确定性占位替身。 */
public class PlaceholderSimulationNodeExecutor implements SimulationNodeExecutor {

    private static final Set<String> TYPES = Set.of("PROMPT", "TOOL", "AGENT", "AGENT_REACT");

    @Override
    public boolean supports(String nodeType) {
        return nodeType != null && TYPES.contains(nodeType.toUpperCase());
    }

    @Override
    public void execute(FlowNodeDefinition node, SimulationContext context) {
        String outputKey = node.getOutputKey();
        if (outputKey == null || outputKey.isBlank()) {
            return;
        }
        Object value = "JSON".equalsIgnoreCase(node.getOutputMode())
                ? Map.of("mock", true, "nodeCode", node.getNodeCode())
                : "[模拟输出:" + node.getNodeCode() + "]";
        context.putSynthetic(outputKey, value);
    }
}
