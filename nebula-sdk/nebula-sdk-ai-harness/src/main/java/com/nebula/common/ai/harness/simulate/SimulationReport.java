package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.harness.draft.DraftIssue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 双引擎模拟器的统一结果。 */
public record SimulationReport(String engineType,
                               SimulationConfidence confidence,
                               List<DraftIssue> issues,
                               Map<String, Object> details) {

    public SimulationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean successful() {
        return issues.stream().noneMatch(issue -> "ERROR".equals(issue.level()));
    }

    public Map<String, Object> toPayload(long revision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("revision", revision);
        payload.put("engineType", engineType);
        payload.put("confidence", confidence.name());
        payload.putAll(details);
        return Map.of("simulation", payload);
    }
}
