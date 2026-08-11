package com.nebula.common.ai.harness.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarnessConversationPropertiesTest {

    @Test
    void defaultsToEnoughIterationsForNodesEdgesAndValidation() {
        HarnessConversationProperties properties = new HarnessConversationProperties();

        assertEquals(30, properties.getMaxIterations());

        properties.setMaxIterations(200);
        assertEquals(100, properties.getMaxIterations());
    }
}
