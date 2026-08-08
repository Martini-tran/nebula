package com.nebula.common.ai.harness.realrun;

import com.nebula.common.ai.flow.store.AiHarnessConfirmation;
import com.nebula.common.ai.flow.store.AiHarnessConfirmationMapper;
import lombok.RequiredArgsConstructor;

/** MySQL 服务端确认存储。 */
@RequiredArgsConstructor
public class DatabaseDraftConfirmationStore implements DraftConfirmationStore {

    private final AiHarnessConfirmationMapper mapper;

    @Override
    public DraftConfirmation createOrRefresh(DraftConfirmationRequest request) {
        AiHarnessConfirmation entity = new AiHarnessConfirmation();
        entity.setConfirmationId(request.confirmationId());
        entity.setAction(request.action());
        entity.setDraftId(request.draftId());
        entity.setDraftRevision(request.draftRevision());
        entity.setUserId(request.userId());
        entity.setSessionId(request.sessionId());
        entity.setInputDigest(request.inputDigest());
        entity.setExpiresAt(request.expiresAt());
        mapper.upsertPending(entity);
        DraftConfirmation created = findOwned(request.confirmationId(), request.userId());
        if (created == null) {
            throw new IllegalStateException("创建真实试跑确认请求失败");
        }
        return created;
    }

    @Override
    public DraftConfirmation findOwned(String confirmationId, Long userId) {
        if (confirmationId == null || confirmationId.isBlank() || userId == null) {
            return null;
        }
        AiHarnessConfirmation entity = mapper.selectOwned(confirmationId, userId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public boolean confirm(String confirmationId, Long userId, String tokenHash) {
        return mapper.confirm(confirmationId, userId, tokenHash) == 1;
    }

    @Override
    public int expireDue() {
        return mapper.expireDue();
    }

    @Override
    public int purgeTerminalBefore(java.time.LocalDateTime before) {
        return mapper.purgeTerminalBefore(before);
    }

    private DraftConfirmation toDomain(AiHarnessConfirmation entity) {
        return new DraftConfirmation(
                entity.getConfirmationId(),
                entity.getAction(),
                entity.getDraftId(),
                entity.getDraftRevision(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getInputDigest(),
                ConfirmationStatus.valueOf(entity.getStatus()),
                entity.getExpiresAt(),
                entity.getConfirmedAt(),
                entity.getConsumedAt());
    }
}
