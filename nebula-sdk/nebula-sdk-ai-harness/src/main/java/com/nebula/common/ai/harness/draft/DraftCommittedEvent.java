package com.nebula.common.ai.harness.draft;

/**
 * 草稿成功提交事件。事件不携带完整定义，避免大对象跨边界传播。
 *
 * @param draftId 草稿 ID
 * @param revision 已提交 revision
 * @param flowCode 正式流程编码
 * @param version 正式流程版本
 * @author nebula
 */
public record DraftCommittedEvent(String draftId, long revision, String flowCode, int version) {
}
