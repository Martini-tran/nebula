package com.nebula.common.ai.harness.draft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用内存草稿仓储：语义对齐数据库实现的 CAS 与状态机约束。
 *
 * <p>与 {@code DraftApplicationServiceTest} 内的私有实现同构，
 * 独立成类供跨测试类复用（不改动既有测试，避免牵连回归）。
 */
public final class TestDraftStore implements DraftStore {

    private final Map<String, FlowDraft> drafts = new ConcurrentHashMap<>();
    private final FlowDefinitionCodec codec;

    public TestDraftStore(FlowDefinitionCodec codec) {
        this.codec = codec;
    }

    @Override
    public synchronized FlowDraft create(FlowDraft draft) {
        drafts.put(draft.getDraftId(), copy(draft));
        return copy(draft);
    }

    @Override
    public synchronized FlowDraft findOwned(String draftId, Long userId) {
        FlowDraft draft = drafts.get(draftId);
        return draft == null || !draft.getUserId().equals(userId) ? null : copy(draft);
    }

    @Override
    public synchronized boolean compareAndSet(FlowDraft draft, long expectedRevision) {
        FlowDraft current = drafts.get(draft.getDraftId());
        if (current == null || current.getRevision() != expectedRevision
                || current.getStatus() != DraftStatus.BUILDING
                || !current.getUserId().equals(draft.getUserId())) {
            return false;
        }
        FlowDraft stored = copy(draft);
        stored.setRevision(expectedRevision + 1);
        drafts.put(stored.getDraftId(), stored);
        return true;
    }

    @Override
    public synchronized boolean markValidated(String draftId, Long userId, long expectedRevision) {
        FlowDraft current = drafts.get(draftId);
        if (current == null || !current.getUserId().equals(userId)
                || current.getRevision() != expectedRevision
                || current.getStatus() != DraftStatus.BUILDING) {
            return false;
        }
        current.setLastValidatedRevision(expectedRevision);
        return true;
    }

    @Override
    public synchronized boolean markSimulated(String draftId, Long userId, long expectedRevision) {
        FlowDraft current = drafts.get(draftId);
        if (current == null || !current.getUserId().equals(userId)
                || current.getRevision() != expectedRevision
                || current.getStatus() != DraftStatus.BUILDING) {
            return false;
        }
        current.setLastValidatedRevision(expectedRevision);
        current.setLastSimulatedRevision(expectedRevision);
        return true;
    }

    /** 当前所有草稿 ID（测试断言用）。 */
    public synchronized java.util.List<String> draftIds() {
        return java.util.List.copyOf(drafts.keySet());
    }

    /** 不做归属校验地直接取草稿（测试断言用）。 */
    public synchronized FlowDraft peek(String draftId) {
        FlowDraft draft = drafts.get(draftId);
        return draft == null ? null : copy(draft);
    }

    synchronized boolean markCommitted(DraftCommitRequest request) {
        FlowDraft current = drafts.get(request.draftId());
        if (current == null || !current.getUserId().equals(request.userId())
                || current.getRevision() != request.revision()
                || !Long.valueOf(request.revision()).equals(current.getLastValidatedRevision())
                || (request.requireSimulation()
                && !Long.valueOf(request.revision()).equals(current.getLastSimulatedRevision()))
                || current.getStatus() != DraftStatus.BUILDING) {
            return false;
        }
        current.setStatus(DraftStatus.COMMITTED);
        current.setCommittedFlowCode(request.definition().getFlowCode());
        return true;
    }

    private FlowDraft copy(FlowDraft source) {
        return new FlowDraft()
                .setDraftId(source.getDraftId())
                .setSessionId(source.getSessionId())
                .setUserId(source.getUserId())
                .setFlowCode(source.getFlowCode())
                .setName(source.getName())
                .setDescription(source.getDescription())
                .setEngineType(source.getEngineType())
                .setRevision(source.getRevision())
                .setLastValidatedRevision(source.getLastValidatedRevision())
                .setLastSimulatedRevision(source.getLastSimulatedRevision())
                .setStatus(source.getStatus())
                .setCommittedFlowCode(source.getCommittedFlowCode())
                .setCreateTime(source.getCreateTime())
                .setUpdateTime(source.getUpdateTime())
                .setGraph(codec.copy(source.getGraph()));
    }
}
