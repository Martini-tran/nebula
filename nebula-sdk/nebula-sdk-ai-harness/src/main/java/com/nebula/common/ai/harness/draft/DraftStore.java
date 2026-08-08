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
}
