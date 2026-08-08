package com.nebula.common.ai.harness.realrun;

import java.util.Map;

/** 草稿真实执行结果，由 Harness 在持久化前再次脱敏和限长。 */
public record DraftRunResult(Map<String, Object> output) {

    public DraftRunResult {
        output = output == null ? Map.of() : Map.copyOf(output);
    }
}
