package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.flow.FlowDefinition;

/**
 * 经 Harness 授权并重新校验后的原子提交请求。
 *
 * @param draftId 草稿业务 ID
 * @param userId 草稿所有者
 * @param revision 待提交 revision
 * @param definition 深拷贝流程定义
 * @author nebula
 */
public record DraftCommitRequest(String draftId,
                                 Long userId,
                                 long revision,
                                 FlowDefinition definition) {
}
