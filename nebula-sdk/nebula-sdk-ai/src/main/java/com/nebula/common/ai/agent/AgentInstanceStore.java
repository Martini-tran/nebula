package com.nebula.common.ai.agent;

import java.util.Map;
import java.util.Set;

/**
 * Agent 状态机实例存储（SPI）
 * 把一次状态机实例的创建、每步转移、终态逐一持久化，使实例可回放 / 续跑，对应 {@code ai_agent_instance} 两表。
 * SDK 仅定义接口；数据库实现由 {@code nebula-sdk-ai-flow} 的 store 包提供，缺省（无实现）时 {@code AgentEngine}
 * 退化为纯内存执行——与 {@code RunStateStore} 的分层完全一致。
 *
 * <p>写方法由 {@code StoreBackedTransitionListener} 在内核推进的各记账点调用，严格遵循<b>先干活后记账</b>：
 * {@link #appendSuccess} 只在节点产物已 apply 后调用，保证"有 SUCCESS 轨迹 ⇒ 该步一定已干成"。
 *
 * <p><b>分级落盘（决策 v3）</b>：RUNNING 中普通转移只 {@link #appendSuccess}/{@link #appendTransition}（轻量），
 * {@code context_snapshot} 不刷；全量刷仅在终态 {@link #markTerminal}/{@link #markFailed}。
 *
 * @author nebula
 */
public interface AgentInstanceStore {

    /**
     * 创建一个状态机实例并标记为 RUNNING。
     *
     * @param definition     Agent 定义（取 agentCode/version、flowCode/version 作审计标记）
     * @param graphSnapshot  ★创建时编译的完整源图定义 JSON（版本锁定核心）
     * @param userId         归属用户ID
     * @param conversationId 关联会话ID
     * @param inputs         初始输入快照
     * @return 实例唯一标识 instanceId
     */
    default String create(AgentDefinition definition, String graphSnapshot,
                          String userId, String conversationId, Map<String, Object> inputs) {
        return create(definition, graphSnapshot, userId, conversationId, inputs, null, null);
    }

    /**
     * 创建一个状态机实例并标记为 RUNNING，可关联父实例（递归子 Agent，阶段 3）。
     *
     * @param definition         Agent 定义
     * @param graphSnapshot      ★创建时编译的完整源图定义 JSON（版本锁定核心）
     * @param userId             归属用户ID
     * @param conversationId     关联会话ID
     * @param inputs             初始输入快照
     * @param parentInstanceId   父实例标识（顶层为 null）
     * @param parentNodeCode     父实例中触发本子实例的 AgentNode 编码（顶层为 null）
     * @return 实例唯一标识 instanceId
     */
    String create(AgentDefinition definition, String graphSnapshot,
                  String userId, String conversationId, Map<String, Object> inputs,
                  String parentInstanceId, String parentNodeCode);

    /**
     * 读取实例快照（回放 / 续跑用）。
     *
     * @param instanceId 实例标识
     * @return 快照；不存在返回 null
     */
    AgentInstanceSnapshot load(String instanceId);

    /**
     * 落一行 {@code outcome=SUCCESS} 的转移：节点执行成功（产物已 apply）。
     *
     * @param instanceId   实例标识
     * @param stateCode    成功的状态编码
     * @param seq          转移序号
     * @param attempt      成功时的尝试次数（0=首次）
     * @param contextDelta 本步 context 变更集（node_result，重放时 apply 它恢复产物）
     */
    void appendSuccess(String instanceId, String stateCode, int seq, int attempt, Map<String, Object> contextDelta);

    /**
     * 落一行 {@code outcome=RETRY} 的转移：节点某次尝试失败但仍将重试（node_result 记错误摘要，重放时按 outcome 过滤）。
     *
     * @param instanceId   实例标识
     * @param stateCode    失败的状态编码
     * @param seq          转移序号
     * @param attempt      失败的尝试次数
     * @param errorSummary 错误摘要
     */
    void appendRetry(String instanceId, String stateCode, int seq, int attempt, String errorSummary);

    /**
     * 记录状态推进：更新实例的 current_state 与 transition_count（不刷 context_snapshot）。
     *
     * @param instanceId 实例标识
     * @param fromState  源状态
     * @param toState    目标状态
     * @param seq        本次转移序号
     */
    void appendTransition(String instanceId, String fromState, String toState, int seq);

    /**
     * 标记实例 SUCCESS 并全量刷 context_snapshot（记 context_snapshot_seq）。
     *
     * @param instanceId      实例标识
     * @param terminalState   终态编码
     * @param contextSnapshot 全量产物快照
     */
    void markTerminal(String instanceId, String terminalState, Map<String, Object> contextSnapshot);

    /**
     * 标记实例 FAILED 并全量刷 context_snapshot。
     *
     * @param instanceId      实例标识
     * @param failedState     失败时所在状态（可能为 null）
     * @param error           失败原因
     * @param contextSnapshot 失败时的产物快照
     */
    void markFailed(String instanceId, String failedState, String error, Map<String, Object> contextSnapshot);

    /**
     * 标记实例 SUSPENDED（挂起等 signal）并全量刷 context_snapshot（分级落盘三时机之一，阶段 3）。
     * {@code lock_version} 不变——唤醒时 {@link #acquireForResume} 以创建时/上次的 lock_version 做 CAS 抢占。
     *
     * @param instanceId      实例标识
     * @param suspendedState  挂起时所在状态编码
     * @param awaitingEvents  等待的事件名集合（落 awaiting_events JSON 数组，signal 命中其一即唤醒）
     * @param contextSnapshot 挂起时的全量产物快照（唤醒时直接加载）
     */
    void markSuspended(String instanceId, String suspendedState, Set<String> awaitingEvents,
                       Map<String, Object> contextSnapshot);

    /**
     * CAS 抢占一个挂起实例的推进权（阶段 3 并发控制）：只有 {@code status='SUSPENDED'} 且 {@code lock_version}
     * 与 {@code expectedLockVersion} 相等时，才把 status 置 RUNNING、{@code lock_version+1}，返回 true；
     * 影响 0 行（已被别的 signal 抢先 / 已非挂起态）返回 false——<b>绝不并发跑同一实例</b>（文档 5.3）。
     *
     * @param instanceId          实例标识
     * @param expectedLockVersion 期望的乐观锁版本（从 {@link #load} 的快照读）
     * @return 抢占成功 true；失败 false
     */
    boolean acquireForResume(String instanceId, int expectedLockVersion);
}
