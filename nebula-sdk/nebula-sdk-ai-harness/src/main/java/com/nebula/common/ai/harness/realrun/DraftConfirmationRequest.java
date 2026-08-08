package com.nebula.common.ai.harness.realrun;

import java.time.LocalDateTime;

/** 创建或刷新真实试跑确认请求所需的服务端字段。 */
public record DraftConfirmationRequest(
        String confirmationId,
        String action,
        String draftId,
        long draftRevision,
        Long userId,
        String sessionId,
        String inputDigest,
        LocalDateTime expiresAt) {
}
