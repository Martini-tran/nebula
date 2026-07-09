package com.nebula.common.ai.orchestration;

import java.util.Map;
import java.util.Set;

/**
 * 编排执行实例快照
 * 一次执行实例在某时刻的落库状态：已执行节点集合 + 全量产物 + 状态 + 归属用户/会话。
 * 续跑时由 {@link RunStateStore#load(String)} 返回，编排器据此恢复 {@code executed} 与 {@code OrchestrationContext.attributes}。
 *
 * @param runId          执行实例标识
 * @param flowCode       流程编码（续跑时据此加载定义、编图）
 * @param version        流程版本
 * @param status         实例状态
 * @param userId         归属用户ID
 * @param conversationId 关联会话ID
 * @param executedNodes  已执行完成的节点编码集合
 * @param attributes     全量产物快照（恢复后灌回上下文）
 * @author nebula
 */
public record RunSnapshot(String runId,
                          String flowCode,
                          int version,
                          RunStatus status,
                          String userId,
                          String conversationId,
                          Set<String> executedNodes,
                          Map<String, Object> attributes) {
}
