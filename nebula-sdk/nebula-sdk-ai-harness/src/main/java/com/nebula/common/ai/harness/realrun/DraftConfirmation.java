package com.nebula.common.ai.harness.realrun;

import java.time.LocalDateTime;

/** 不暴露令牌哈希的确认领域快照。 */
public record DraftConfirmation(
        String confirmationId,
        String action,
        String draftId,
        long draftRevision,
        Long userId,
        String sessionId,
        String inputDigest,
        ConfirmationStatus status,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt,
        LocalDateTime consumedAt) {
}
