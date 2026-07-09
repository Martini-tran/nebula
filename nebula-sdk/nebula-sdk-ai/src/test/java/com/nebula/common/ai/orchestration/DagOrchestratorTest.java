package com.nebula.common.ai.orchestration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagOrchestratorTest {

    private final Orchestrator orchestrator = new DagOrchestrator();

    @Test
    void linearChainExecutesInOrder() {
        List<String> order = new ArrayList<>();
        OrchestrationGraph graph = OrchestrationGraph.builder("linear")
                .node("A", ctx -> order.add("A"))
                .node("B", ctx -> order.add("B"))
                .node("C", ctx -> order.add("C"))
                .edge("A", "B")
                .edge("B", "C")
                .build();

        orchestrator.run(graph, new OrchestrationContext());

        assertEquals(List.of("A", "B", "C"), order);
    }

    @Test
    void conditionalBranchSkipsUnreachable() {
        List<String> executed = new ArrayList<>();
        OrchestrationGraph graph = OrchestrationGraph.builder("branch")
                .node("A", ctx -> executed.add("A"))
                .node("B", ctx -> executed.add("B"))
                .node("C", ctx -> executed.add("C"))
                .edge("A", "B", ctx -> true)
                .edge("A", "C", ctx -> false)
                .build();

        orchestrator.run(graph, new OrchestrationContext());

        assertTrue(executed.contains("A"));
        assertTrue(executed.contains("B"));
        assertFalse(executed.contains("C"));
    }

    @Test
    void diamondJoinExecutesOnce() {
        List<String> executed = new ArrayList<>();
        OrchestrationGraph graph = OrchestrationGraph.builder("diamond")
                .node("A", ctx -> executed.add("A"))
                .node("B", ctx -> executed.add("B"))
                .node("C", ctx -> executed.add("C"))
                .node("D", ctx -> executed.add("D"))
                .edge("A", "B")
                .edge("A", "C")
                .edge("B", "D")
                .edge("C", "D")
                .build();

        orchestrator.run(graph, new OrchestrationContext());

        assertEquals(1, Collections.frequency(executed, "D"));
        assertTrue(executed.indexOf("D") > executed.indexOf("B"));
        assertTrue(executed.indexOf("D") > executed.indexOf("C"));
    }

    @Test
    void nodeExceptionPropagates() {
        OrchestrationGraph graph = OrchestrationGraph.builder("boom")
                .node("A", ctx -> {
                    throw new IllegalStateException("boom");
                })
                .build();

        assertThrows(IllegalStateException.class, () -> orchestrator.run(graph, new OrchestrationContext()));
    }

    @Test
    void downstreamOfSkippedNodeAlsoSkipped() {
        List<String> executed = new ArrayList<>();
        OrchestrationGraph graph = OrchestrationGraph.builder("chain-skip")
                .node("A", ctx -> executed.add("A"))
                .node("B", ctx -> executed.add("B"))
                .node("C", ctx -> executed.add("C"))
                .edge("A", "B", ctx -> false)
                .edge("B", "C")
                .build();

        orchestrator.run(graph, new OrchestrationContext());

        assertEquals(List.of("A"), executed);
    }
}
