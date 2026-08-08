package com.nebula.common.ai.harness.realrun;

/** 确认消费后创建唯一 operation 所需字段。 */
public record HarnessOperationRequest(
        String operationId,
        String action,
        String draftId,
        long draftRevision,
        Long userId,
        String sessionId,
        String inputDigest) {
}
