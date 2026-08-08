package com.nebula.common.ai.harness.realrun;

import java.time.LocalDateTime;
import java.util.Map;

/** 高风险操作的持久化领域快照。 */
public record HarnessOperation(
        String operationId,
        String action,
        String draftId,
        long draftRevision,
        Long userId,
        String sessionId,
        String inputDigest,
        HarnessOperationStatus status,
        Map<String, Object> resultSummary,
        String errorCode,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime heartbeatAt,
        LocalDateTime finishedAt) {

    public HarnessOperation {
        resultSummary = resultSummary == null ? Map.of() : Map.copyOf(resultSummary);
    }
}
