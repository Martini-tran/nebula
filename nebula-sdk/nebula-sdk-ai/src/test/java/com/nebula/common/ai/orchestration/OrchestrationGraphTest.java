package com.nebula.common.ai.orchestration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrchestrationGraphTest {

    private static final OrchestrationNode NOOP = ctx -> {
    };

    @Test
    void buildValidGraphAndQueryRoots() {
        OrchestrationGraph graph = OrchestrationGraph.builder("t")
                .node("A", NOOP)
                .node("B", NOOP)
                .edge("A", "B")
                .build();

        assertEquals(List.of("A"), graph.roots());
        assertEquals(1, graph.outEdges("A").size());
        assertEquals(1, graph.inEdges("B").size());
    }

    @Test
    void edgeToUnknownNodeThrows() {
        OrchestrationGraph.Builder builder = OrchestrationGraph.builder("t")
                .node("A", NOOP)
                .edge("A", "ghost");

        assertThrows(OrchestrationException.class, builder::build);
    }

    @Test
    void duplicateNodeThrows() {
        OrchestrationGraph.Builder builder = OrchestrationGraph.builder("t").node("A", NOOP);

        assertThrows(OrchestrationException.class, () -> builder.node("A", NOOP));
    }

    @Test
    void cycleThrows() {
        OrchestrationGraph.Builder builder = OrchestrationGraph.builder("t")
                .node("A", NOOP)
                .node("B", NOOP)
                .edge("A", "B")
                .edge("B", "A");

        assertThrows(OrchestrationException.class, builder::build);
    }
}
