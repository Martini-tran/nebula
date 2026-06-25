package com.nebula.common.ai.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationContextTest {

    @Test
    void putReturnsSelfAndGetReadsBack() {
        OrchestrationContext ctx = new OrchestrationContext();
        OrchestrationContext same = ctx.put("topic", "java").put("count", 5);

        assertSame(ctx, same);
        assertEquals("java", ctx.get("topic"));
        assertEquals(5, ctx.get("count"));
    }

    @Test
    void typedGetReturnsValueOrNullOnMismatch() {
        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put("count", 42);

        assertEquals(42, ctx.get("count", Integer.class));
        assertNull(ctx.get("count", String.class));
        assertNull(ctx.get("missing", Integer.class));
    }

    @Test
    void getStringAndContains() {
        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put("count", 7);

        assertEquals("7", ctx.getString("count"));
        assertNull(ctx.getString("missing"));
        assertTrue(ctx.contains("count"));
        assertFalse(ctx.contains("missing"));
    }

    @Test
    void putIgnoresNullKeyOrValue() {
        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put(null, "x").put("k", null);

        assertFalse(ctx.contains("k"));
        assertTrue(ctx.attributes().isEmpty());
    }

    @Test
    void nodeResultsKeepInsertionOrder() {
        OrchestrationContext ctx = new OrchestrationContext();
        ctx.recordResult("A", Boolean.TRUE);
        ctx.recordResult("B", Boolean.TRUE);

        assertEquals("[A, B]", ctx.nodeResults().keySet().toString());
    }
}
