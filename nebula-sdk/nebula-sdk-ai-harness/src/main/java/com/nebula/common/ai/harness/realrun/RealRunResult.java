package com.nebula.common.ai.harness.realrun;

import com.nebula.common.ai.harness.draft.DraftIssue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 真实试跑应用服务结果，同时供工具和 HTTP 传输层投影。 */
public record RealRunResult(
        boolean ok,
        String code,
        String message,
        String hint,
        List<DraftIssue> issues,
        Map<String, Object> payload) {

    public RealRunResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static RealRunResult success(Map<String, Object> payload) {
        return new RealRunResult(true, null, null, null, List.of(), payload);
    }

    public static RealRunResult failure(String code, String message, String hint) {
        return new RealRunResult(false, code, message, hint, List.of(), Map.of());
    }

    public static RealRunResult failure(List<DraftIssue> issues) {
        return new RealRunResult(false, "DRAFT_VALIDATION_FAILED",
                "草稿未通过真实试跑前置校验", "根据 issues 修正后重新模拟", issues, Map.of());
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", ok);
        if (code != null) {
            response.put("code", code);
        }
        if (message != null) {
            response.put("message", message);
        }
        if (hint != null) {
            response.put("hint", hint);
        }
        if (!issues.isEmpty()) {
            response.put("issues", new ArrayList<>(issues));
        }
        response.putAll(payload);
        return response;
    }
}
