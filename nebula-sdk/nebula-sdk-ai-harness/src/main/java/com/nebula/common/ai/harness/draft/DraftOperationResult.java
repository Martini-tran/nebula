package com.nebula.common.ai.harness.draft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 草稿应用服务结果。工具层只负责将该结果投影为统一 JSON 契约。
 *
 * @author nebula
 */
public record DraftOperationResult(boolean ok,
                                   String draftId,
                                   Long revision,
                                   List<DraftIssue> issues,
                                   Map<String, Object> payload) {

    public DraftOperationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static DraftOperationResult success(FlowDraft draft, Map<String, Object> payload) {
        return new DraftOperationResult(true, draft.getDraftId(), draft.getRevision(), List.of(), payload);
    }

    public static DraftOperationResult success(FlowDraft draft, List<DraftIssue> issues,
                                               Map<String, Object> payload) {
        return new DraftOperationResult(true, draft.getDraftId(), draft.getRevision(), issues, payload);
    }

    public static DraftOperationResult failure(String draftId, Long revision, DraftIssue issue) {
        return new DraftOperationResult(false, draftId, revision, List.of(issue), Map.of());
    }

    public static DraftOperationResult failure(String draftId, Long revision, List<DraftIssue> issues,
                                               Map<String, Object> payload) {
        return new DraftOperationResult(false, draftId, revision, issues, payload);
    }

    public Map<String, Object> toToolResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", ok);
        if (draftId != null) {
            response.put("draftId", draftId);
        }
        if (revision != null) {
            response.put("revision", revision);
        }
        if (!issues.isEmpty()) {
            response.put("issues", new ArrayList<>(issues));
        }
        response.putAll(payload);
        return response;
    }
}
