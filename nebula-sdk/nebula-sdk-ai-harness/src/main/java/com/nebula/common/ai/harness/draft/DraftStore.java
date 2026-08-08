package com.nebula.common.ai.harness.draft;

/**
 * 草稿持久化 SPI。
 *
 * <p>实现必须让每次读取返回独立对象，并以单条数据库语句执行整图 CAS。
 *
 * @author nebula
 */
public interface DraftStore {

    FlowDraft create(FlowDraft draft);

    FlowDraft findOwned(String draftId, Long userId);

    boolean compareAndSet(FlowDraft draft, long expectedRevision);

    /**
     * 标记当前 revision 已通过无 ERROR 的完整校验，不推进 revision。
     *
     * @return 仅当草稿仍是同一 BUILDING revision 时返回 true
     */
    boolean markValidated(String draftId, Long userId, long expectedRevision);
}
