package com.nebula.common.ai.harness.draft;

/**
 * 草稿提交到正式流程存储的宿主 SPI。
 *
 * <p>实现必须在一个事务内完成正式流程 CREATE_ONLY 写入和草稿状态 CAS，不得调用覆盖式 upsert。
 *
 * @author nebula
 */
public interface DraftCommitter {

    DraftCommitResult commit(DraftCommitRequest request);
}
