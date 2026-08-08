package com.nebula.common.ai.harness.realrun;

import java.time.LocalDateTime;
import java.util.Map;

/** 高风险操作幂等与状态持久化 SPI。 */
public interface HarnessOperationStore {

    HarnessOperation findByKey(String action, String draftId, long draftRevision, Long userId);

    HarnessOperation findOperationOwned(String operationId, Long userId);

    /** 实现必须在一个事务中消费确认令牌并创建或取得唯一 operation。 */
    OperationAuthorization authorizeAndCreate(String tokenHash, HarnessOperationRequest request);

    boolean claim(String operationId);

    boolean heartbeat(String operationId);

    boolean markSucceeded(String operationId, Map<String, Object> resultSummary);

    boolean markFailed(String operationId, String errorCode, String errorMessage);

    int markUnknownStale(LocalDateTime staleBefore);

    int expireResultDetails(LocalDateTime before);
}
