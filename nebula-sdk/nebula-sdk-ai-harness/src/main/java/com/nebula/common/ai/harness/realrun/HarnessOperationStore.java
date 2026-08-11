package com.nebula.common.ai.harness.realrun;

import java.time.LocalDateTime;
import java.util.Map;

/** 高风险操作幂等与状态持久化 SPI。 */
public interface HarnessOperationStore {

    HarnessOperation findByKey(String action, String draftId, long draftRevision, Long userId);

    HarnessOperation findOperationOwned(String operationId, Long userId);

    /**
     * 实现必须在一个事务中消费确认令牌，并创建唯一 operation，或将 FAILED/UNKNOWN operation
     * 原子重置为 PENDING。PENDING/RUNNING/SUCCEEDED 不得被重置。
     */
    OperationAuthorization authorizeAndCreateOrRetry(String tokenHash, HarnessOperationRequest request);

    boolean claim(String operationId);

    boolean heartbeat(String operationId);

    boolean markSucceeded(String operationId, Map<String, Object> resultSummary);

    boolean markFailed(String operationId, String errorCode, String errorMessage);

    int markUnknownStale(LocalDateTime staleBefore);

    int expireResultDetails(LocalDateTime before);
}
