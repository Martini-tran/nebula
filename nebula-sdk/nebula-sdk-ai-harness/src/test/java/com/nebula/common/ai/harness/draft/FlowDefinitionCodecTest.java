package com.nebula.common.ai.harness.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowDefinitionCodecTest {

    @Test
    void rejectsMalformedAndUnknownPersistedFields() {
        FlowDefinitionCodec codec = new FlowDefinitionCodec(new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> codec.read("{"));
        assertThrows(IllegalStateException.class,
                () -> codec.read("{\"flowCode\":\"x\",\"unknownPersistedField\":true}"));
    }
}
