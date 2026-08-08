package com.nebula.common.ai.harness.simulate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftSimulatorTest {

    @Test
    void dagUsesOnlySimulationExecutorsIncludingBoundedLoopMembers() {
        HarnessSimulationProperties properties = new HarnessSimulationProperties();
        AtomicInteger simulatedBusinessNodes = new AtomicInteger();
        SimulationNodeExecutor recording = new SimulationNodeExecutor() {
            @Override
            public boolean supports(String nodeType) {
                return List.of("PROMPT", "TOOL", "AGENT", "AGENT_REACT").contains(nodeType);
            }

            @Override
            public void execute(FlowNodeDefinition node, SimulationContext context) {
                simulatedBusinessNodes.incrementAndGet();
                context.putSynthetic(node.getOutputKey(), "mock");
            }
        };
        DraftSimulator simulator = simulator(properties, recording);
        FlowDefinition definition = dagWithLoop();

        SimulationReport report = simulator.simulate(definition, Map.of("request", "demo"));

        assertTrue(report.successful());
        assertEquals(SimulationConfidence.CONFIRMED, report.confidence());
        assertEquals(5, simulatedBusinessNodes.get());
        List<?> reached = (List<?>) report.details().get("reachedNodes");
        assertTrue(reached.containsAll(List.of("start", "prompt", "tool", "react", "loop", "member", "end")));
    }

    @Test
    void rejectsOversizedInitialInputBeforeExecutingAnyNode() {
        HarnessSimulationProperties properties = new HarnessSimulationProperties();
        properties.setMaxInitialInputBytes(8);
        AtomicInteger executions = new AtomicInteger();
        SimulationNodeExecutor recording = new SimulationNodeExecutor() {
            @Override
            public boolean supports(String nodeType) {
                return true;
            }

            @Override
            public void execute(FlowNodeDefinition node, SimulationContext context) {
                executions.incrementAndGet();
            }
        };

        SimulationReport report = simulator(properties, recording)
                .simulate(dagWithLoop(), Map.of("long", "value-over-budget"));

        assertFalse(report.successful());
        assertEquals("SIMULATION_INPUT_LIMIT", report.issues().getFirst().code());
        assertEquals(0, executions.get());
    }

    private DraftSimulator simulator(HarnessSimulationProperties properties,
                                     SimulationNodeExecutor businessExecutor) {
        SimulationExecutorRegistry registry = new SimulationExecutorRegistry(List.of(
                new StructuralSimulationNodeExecutor(), businessExecutor));
        SimulationConditionEvaluator evaluator = new SimulationConditionEvaluator(new ConditionCompiler());
        SimulationLoopDriver loopDriver = new SimulationLoopDriver(properties, registry);
        return new DraftSimulator(List.of(new DagSimulator(properties, registry, loopDriver, evaluator)),
                new SimulationInputValidator(new ObjectMapper(), properties));
    }

    private FlowDefinition dagWithLoop() {
        FlowDefinition definition = new FlowDefinition().setFlowCode("simulation-isolation").setEngineType("DAG");
        definition.setNodes(new ArrayList<>(List.of(
                node("start", "START", null, 0),
                node("prompt", "PROMPT", "promptOutput", 1),
                node("tool", "TOOL", "toolOutput", 2),
                node("react", "AGENT_REACT", "reactOutput", 3),
                loop("loop", List.of("member"), 4),
                node("member", "AGENT", "memberOutput", 5),
                node("end", "END", null, 6))));
        definition.setEdges(new ArrayList<>(List.of(
                edge("start", "prompt", 0),
                edge("prompt", "tool", 1),
                edge("tool", "react", 2),
                edge("react", "member", 3),
                edge("member", "end", 4))));
        return definition;
    }

    private FlowNodeDefinition node(String code, String type, String outputKey, int sortNo) {
        return new FlowNodeDefinition().setNodeCode(code).setNodeType(type)
                .setOutputKey(outputKey).setSortNo(sortNo);
    }

    private FlowNodeDefinition loop(String code, List<String> members, int sortNo) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("members", members);
        return node(code, "LOOP", "loopOutput", sortNo).setNodeConfig(config);
    }

    private FlowEdgeDefinition edge(String from, String to, int sortNo) {
        return new FlowEdgeDefinition().setFromNode(from).setToNode(to).setSortNo(sortNo);
    }
}
