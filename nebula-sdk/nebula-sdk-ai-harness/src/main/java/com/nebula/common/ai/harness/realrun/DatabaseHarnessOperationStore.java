package com.nebula.common.ai.harness.realrun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.store.AiHarnessConfirmationMapper;
import com.nebula.common.ai.flow.store.AiHarnessOperation;
import com.nebula.common.ai.flow.store.AiHarnessOperationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** MySQL Harness operation 存储，以事务和条件 SQL 实现一次性授权与状态 CAS。 */
@RequiredArgsConstructor
public class DatabaseHarnessOperationStore implements HarnessOperationStore {

    private final AiHarnessConfirmationMapper confirmationMapper;
    private final AiHarnessOperationMapper operationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public HarnessOperation findByKey(String action, String draftId, long draftRevision, Long userId) {
        AiHarnessOperation entity = operationMapper.selectByKey(action, draftId, draftRevision, userId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public HarnessOperation findOperationOwned(String operationId, Long userId) {
        if (operationId == null || operationId.isBlank() || userId == null) {
            return null;
        }
        AiHarnessOperation entity = operationMapper.selectOwned(operationId, userId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationAuthorization authorizeAndCreateOrRetry(String tokenHash, HarnessOperationRequest request) {
        int consumed = confirmationMapper.consume(
                tokenHash,
                request.action(),
                request.draftId(),
                request.draftRevision(),
                request.userId(),
                request.inputDigest());
        if (consumed != 1) {
            return OperationAuthorization.rejected();
        }

        AiHarnessOperation entity = new AiHarnessOperation();
        entity.setOperationId(request.operationId());
        entity.setAction(request.action());
        entity.setDraftId(request.draftId());
        entity.setDraftRevision(request.draftRevision());
        entity.setUserId(request.userId());
        entity.setSessionId(request.sessionId());
        entity.setInputDigest(request.inputDigest());
        int inserted = operationMapper.insertOperation(entity);
        if (inserted == 0) {
            operationMapper.resetTerminalForRetry(entity);
        }

        HarnessOperation operation = findByKey(
                request.action(), request.draftId(), request.draftRevision(), request.userId());
        if (operation == null) {
            throw new IllegalStateException("确认已消费但未能创建真实试跑操作");
        }
        if ((operation.status() == HarnessOperationStatus.FAILED
                || operation.status() == HarnessOperationStatus.UNKNOWN)) {
            throw new IllegalStateException("确认已消费但未能重置真实试跑操作");
        }
        return OperationAuthorization.authorized(operation);
    }

    @Override
    public boolean claim(String operationId) {
        return operationMapper.claim(operationId) == 1;
    }

    @Override
    public boolean heartbeat(String operationId) {
        return operationMapper.heartbeat(operationId) == 1;
    }

    @Override
    public boolean markSucceeded(String operationId, Map<String, Object> resultSummary) {
        try {
            return operationMapper.markSucceeded(operationId, objectMapper.writeValueAsString(resultSummary)) == 1;
        } catch (Exception e) {
            throw new IllegalStateException("序列化真实试跑结果摘要失败", e);
        }
    }

    @Override
    public boolean markFailed(String operationId, String errorCode, String errorMessage) {
        return operationMapper.markFailed(operationId, errorCode, errorMessage) == 1;
    }

    @Override
    public int markUnknownStale(LocalDateTime staleBefore) {
        return operationMapper.markUnknownStale(staleBefore);
    }

    @Override
    public int expireResultDetails(LocalDateTime before) {
        return operationMapper.expireResultDetails(before);
    }

    private HarnessOperation toDomain(AiHarnessOperation entity) {
        return new HarnessOperation(
                entity.getOperationId(),
                entity.getAction(),
                entity.getDraftId(),
                entity.getDraftRevision(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getInputDigest(),
                HarnessOperationStatus.valueOf(entity.getStatus()),
                readSummary(entity.getResultSummaryJson()),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getHeartbeatAt(),
                entity.getFinishedAt());
    }

    private Map<String, Object> readSummary(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("解析真实试跑结果摘要失败", e);
        }
    }
}
