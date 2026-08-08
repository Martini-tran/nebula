package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.flow.store.AiFlowDraft;
import com.nebula.common.ai.flow.store.AiFlowDraftMapper;
import lombok.RequiredArgsConstructor;

/**
 * 数据库草稿存储实现。
 *
 * @author nebula
 */
@RequiredArgsConstructor
public class DatabaseDraftStore implements DraftStore {

    private final AiFlowDraftMapper mapper;

    private final FlowDefinitionCodec codec;

    @Override
    public FlowDraft create(FlowDraft draft) {
        AiFlowDraft entity = toEntity(draft);
        if (mapper.insertDraft(entity) != 1) {
            throw new IllegalStateException("创建流程草稿失败");
        }
        return findOwned(draft.getDraftId(), draft.getUserId());
    }

    @Override
    public FlowDraft findOwned(String draftId, Long userId) {
        if (draftId == null || draftId.isBlank() || userId == null) {
            return null;
        }
        AiFlowDraft entity = mapper.selectOwned(draftId, userId);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public boolean compareAndSet(FlowDraft draft, long expectedRevision) {
        return mapper.compareAndSet(toEntity(draft), expectedRevision) == 1;
    }

    @Override
    public boolean markValidated(String draftId, Long userId, long expectedRevision) {
        return mapper.markValidated(draftId, userId, expectedRevision) == 1;
    }

    private AiFlowDraft toEntity(FlowDraft draft) {
        AiFlowDraft entity = new AiFlowDraft();
        entity.setDraftId(draft.getDraftId());
        entity.setSessionId(draft.getSessionId());
        entity.setUserId(draft.getUserId());
        entity.setFlowCode(draft.getFlowCode());
        entity.setName(draft.getName());
        entity.setDescription(draft.getDescription());
        entity.setEngineType(draft.getEngineType());
        entity.setGraphJson(codec.write(draft.getGraph()));
        entity.setRevision(draft.getRevision());
        entity.setLastValidatedRevision(draft.getLastValidatedRevision());
        entity.setLastSimulatedRevision(draft.getLastSimulatedRevision());
        entity.setStatus(draft.getStatus().name());
        entity.setCommittedFlowCode(draft.getCommittedFlowCode());
        return entity;
    }

    private FlowDraft toDomain(AiFlowDraft entity) {
        return new FlowDraft()
                .setDraftId(entity.getDraftId())
                .setSessionId(entity.getSessionId())
                .setUserId(entity.getUserId())
                .setFlowCode(entity.getFlowCode())
                .setName(entity.getName())
                .setDescription(entity.getDescription())
                .setEngineType(entity.getEngineType())
                .setRevision(entity.getRevision() == null ? 0 : entity.getRevision())
                .setLastValidatedRevision(entity.getLastValidatedRevision())
                .setLastSimulatedRevision(entity.getLastSimulatedRevision())
                .setStatus(DraftStatus.valueOf(entity.getStatus()))
                .setCommittedFlowCode(entity.getCommittedFlowCode())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime())
                .setGraph(codec.read(entity.getGraphJson()));
    }
}
