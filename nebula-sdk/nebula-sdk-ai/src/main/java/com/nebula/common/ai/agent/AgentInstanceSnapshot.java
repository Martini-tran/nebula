package com.nebula.common.ai.agent;

import java.util.Map;

/**
 * Agent 状态机实例快照
 * 从 {@link AgentInstanceStore#load(String)} 读回一个实例的自包含运行态，用于回放 / 续跑（续跑执行路径阶段 4）。
 * 核心是 {@code graphSnapshot}——创建时编译的完整源图定义 JSON，续跑/回放只从它加载图，<b>绝不回查 node/edge 表</b>，
 * 天然免疫"别人改了流程"（版本锁定）。
 *
 * @param instanceId         实例唯一标识
 * @param agentCode          归属 Agent 编码（记忆隔离键）
 * @param agentVersion       审计标记：从哪个 Agent 版本创建
 * @param flowCode           审计标记：引用的编排图编码
 * @param flowVersion        审计标记：从哪个 Flow 版本创建
 * @param graphSnapshot      ★版本锁定核心：创建时编译的完整源图定义 JSON（FlowDefinition 序列化）
 * @param userId             归属用户ID
 * @param conversationId     关联会话ID
 * @param status             实例状态：RUNNING | SUSPENDED | SUCCESS | FAILED
 * @param currentState       状态机当前状态（节点编码）
 * @param inputs             初始输入快照
 * @param contextSnapshot    分区上下文快照（可能为空，contextSnapshotSeq=-1 表示还没全量快照）
 * @param contextSnapshotSeq 快照对应的 transition seq，恢复时从此 seq 之后重放增量
 * @param transitionCount    已转移次数
 *
 * @author nebula
 */
public record AgentInstanceSnapshot(
        String instanceId,
        String agentCode,
        int agentVersion,
        String flowCode,
        int flowVersion,
        String graphSnapshot,
        String userId,
        String conversationId,
        String status,
        String currentState,
        Map<String, Object> inputs,
        Map<String, Object> contextSnapshot,
        int contextSnapshotSeq,
        int transitionCount) {
}
