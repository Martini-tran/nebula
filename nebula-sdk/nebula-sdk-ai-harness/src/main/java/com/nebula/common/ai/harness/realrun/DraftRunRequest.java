package com.nebula.common.ai.harness.realrun;

import com.nebula.common.ai.flow.FlowDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** DraftRunner 的宿主无关请求。 */
public record DraftRunRequest(
        String operationId,
        FlowDefinition definition,
        Map<String, Object> initialInput,
        String userId,
        String sessionId) {

    public DraftRunRequest {
        initialInput = initialInput == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(initialInput));
    }
}
