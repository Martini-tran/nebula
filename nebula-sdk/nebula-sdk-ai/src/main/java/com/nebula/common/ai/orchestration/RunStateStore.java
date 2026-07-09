package com.nebula.common.ai.orchestration;

import java.util.Map;
import java.util.Set;

/**
 * 编排执行状态存储（SPI）
 * 把一次 flow 编排执行的进度与产物逐节点持久化，使执行中断后能从断点续跑，而非从头重来。
 * SDK 仅定义接口；数据库实现由 {@code nebula-sdk-ai-flow} 的 store 包提供（落 ai_flow_run / ai_flow_run_node 两表），
 * 缺省（无该实现）时编排退化为纯内存执行，行为与历史一致。
 *
 * <p>由 {@code DagOrchestrator} 在执行各节点前后调用：开始建实例、每节点完成即快照、终态标记成功/失败。
 *
 * @author nebula
 */
public interface RunStateStore {

    /**
     * 创建一次执行实例并标记为 {@link RunStatus#RUNNING}。
     *
     * @param flowCode       流程编码
     * @param version        流程版本
     * @param userId         归属用户ID
     * @param conversationId 关联会话ID
     * @param input          初始输入快照
     * @return 执行实例唯一标识 runId
     */
    String startRun(String flowCode, int version, String userId, String conversationId, Map<String, Object> input);

    /**
     * 读取执行实例快照（续跑用）。
     *
     * @param runId 执行实例标识
     * @return 快照；不存在返回 null
     */
    RunSnapshot load(String runId);

    /**
     * 单节点执行完成后落库：记录该节点轨迹，并更新实例的已执行节点集与全量产物快照。
     *
     * @param runId              执行实例标识
     * @param nodeCode           完成的节点编码
     * @param seq                执行序（本次执行内自增）
     * @param attributesSnapshot 当前全量产物快照
     */
    void saveNodeDone(String runId, String nodeCode, int seq, Map<String, Object> attributesSnapshot);

    /**
     * 标记实例为 {@link RunStatus#SUCCESS} 并落最终产物快照。
     *
     * @param runId              执行实例标识
     * @param attributesSnapshot 最终全量产物快照
     */
    void markFinished(String runId, Map<String, Object> attributesSnapshot);

    /**
     * 标记实例为 {@link RunStatus#FAILED} 并保留进度。
     *
     * @param runId              执行实例标识
     * @param failedNodeCode     失败节点编码
     * @param error              失败原因
     * @param attributesSnapshot 失败时的产物快照
     */
    void markFailed(String runId, String failedNodeCode, String error, Map<String, Object> attributesSnapshot);

    /**
     * 读取实例已执行节点集合（便捷方法，等价于 {@code load(runId).executedNodes()}）。
     *
     * @param runId 执行实例标识
     * @return 已执行节点编码集合；实例不存在返回空集
     */
    default Set<String> executedNodes(String runId) {
        RunSnapshot snapshot = load(runId);
        return snapshot == null ? Set.of() : snapshot.executedNodes();
    }
}
