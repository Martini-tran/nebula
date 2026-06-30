package com.nebula.common.ai.orchestration;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 编排器默认实现
 * 按拓扑序单线程遍历节点。一个节点在其全部前驱「结算」（执行或跳过）后结算：
 * 根节点恒可达；非根节点当且仅当存在一条「来自已执行前驱且条件成立」的入边时可达，
 * 否则跳过——以此实现条件分支/状态机语义。节点抛出的异常原样向上传播（保留业务异常语义），
 * 仅记录日志。并行执行可由实现同一{@link Orchestrator}接口的其他策略后续补充。
 *
 * <p>当注入 {@link RunStateStore} 且 {@link RunContext} 携带 runId 时，开启状态持久化：每个节点执行完即把
 * 「已执行节点集 + 全量产物」落库，节点失败标记实例为 FAILED 并保留进度，从而支持按 runId 断点续跑
 * （续跑时恢复已执行集与产物快照，跳过已完成节点）。无 store 或无 runId 时退化为纯内存执行，行为与历史一致。
 *
 * @author nebula
 */
@Slf4j
public class DagOrchestrator implements Orchestrator {

    /**
     * 编排执行状态存储，可空。为空时退化为纯内存执行（不落库、不支持续跑）。
     */
    private final RunStateStore runStateStore;

    public DagOrchestrator() {
        this(null);
    }

    public DagOrchestrator(RunStateStore runStateStore) {
        this.runStateStore = runStateStore;
    }

    @Override
    public OrchestrationContext run(OrchestrationGraph graph, OrchestrationContext ctx) {
        return run(graph, ctx, null);
    }

    @Override
    public OrchestrationContext run(OrchestrationGraph graph, OrchestrationContext ctx, RunContext runContext) {
        if (graph == null) {
            throw new OrchestrationException("编排图不能为空");
        }
        OrchestrationContext context = ctx == null ? new OrchestrationContext() : ctx;
        boolean persistent = runStateStore != null && runContext != null && runContext.persistent();
        String runId = persistent ? runContext.runId() : null;

        Set<String> executed = new HashSet<>();
        // 续跑：恢复已执行节点集与产物快照，使已完成节点被跳过、下游节点能读到上游产物
        if (persistent && runContext.resume()) {
            restoreFromSnapshot(runId, context, executed);
        }

        List<String> order = topologicalOrder(graph);
        int seq = executed.size();
        for (String id : order) {
            if (executed.contains(id)) {
                log.info("编排[{}] 跳过已执行节点[{}]（续跑）", graph.code(), id);
                continue;
            }
            if (!isReachable(graph, id, executed, context)) {
                log.info("编排[{}] 跳过节点[{}]（条件不可达）", graph.code(), id);
                continue;
            }
            log.info("编排[{}] 执行节点[{}]", graph.code(), id);
            try {
                graph.node(id).execute(context);
            } catch (RuntimeException e) {
                log.error("编排[{}] 节点[{}]执行失败: {}", graph.code(), id, e.getMessage());
                if (runId != null) {
                    safeMarkFailed(runId, id, e.getMessage(), context);
                }
                throw e;
            }
            executed.add(id);
            context.recordResult(id, Boolean.TRUE);
            if (runId != null) {
                safeSaveNodeDone(runId, id, ++seq, context);
            }
            log.info("编排[{}] 完成节点[{}]", graph.code(), id);
        }
        if (runId != null) {
            safeMarkFinished(runId, context);
        }
        return context;
    }

    /**
     * 从落库快照恢复执行进度：已执行节点集 + 全量产物灌回上下文。
     */
    private void restoreFromSnapshot(String runId, OrchestrationContext context, Set<String> executed) {
        RunSnapshot snapshot = runStateStore.load(runId);
        if (snapshot == null) {
            log.warn("续跑找不到执行实例快照[{}]，按全新执行处理", runId);
            return;
        }
        if (snapshot.executedNodes() != null) {
            executed.addAll(snapshot.executedNodes());
        }
        if (snapshot.attributes() != null) {
            snapshot.attributes().forEach(context::put);
        }
        log.info("编排续跑[{}] 恢复已执行节点 {} 个", runId, executed.size());
    }

    /**
     * 节点是否可达：根节点恒可达；否则需存在一条来自已执行前驱且条件成立的入边
     */
    private boolean isReachable(OrchestrationGraph graph, String id, Set<String> executed, OrchestrationContext ctx) {
        List<OrchestrationEdge> inEdges = graph.inEdges(id);
        if (inEdges.isEmpty()) {
            return true;
        }
        for (OrchestrationEdge edge : inEdges) {
            if (executed.contains(edge.from()) && edge.isSatisfied(ctx)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kahn 拓扑排序，保证前驱先于后继处理；存在环则抛出异常（防御，正常已在 build 时拦截）
     */
    private List<String> topologicalOrder(OrchestrationGraph graph) {
        Map<String, Integer> indegree = new HashMap<>();
        for (String id : graph.nodeIds()) {
            indegree.put(id, 0);
        }
        for (OrchestrationEdge edge : graph.edges()) {
            indegree.merge(edge.to(), 1, Integer::sum);
        }
        Deque<String> queue = new ArrayDeque<>();
        for (String id : graph.nodeIds()) {
            if (indegree.get(id) == 0) {
                queue.add(id);
            }
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);
            for (OrchestrationEdge edge : graph.outEdges(current)) {
                if (indegree.merge(edge.to(), -1, Integer::sum) == 0) {
                    queue.add(edge.to());
                }
            }
        }
        if (order.size() != graph.nodeIds().size()) {
            throw new OrchestrationException("编排图存在环，无法拓扑执行");
        }
        return order;
    }

    /**
     * 落库节点完成快照；落库失败不阻断编排主流程（仅告警，续跑能力降级）。
     */
    private void safeSaveNodeDone(String runId, String nodeCode, int seq, OrchestrationContext context) {
        try {
            runStateStore.saveNodeDone(runId, nodeCode, seq, context.attributes());
        } catch (Exception e) {
            log.warn("编排实例[{}] 节点[{}]进度落库失败: {}", runId, nodeCode, e.getMessage());
        }
    }

    private void safeMarkFinished(String runId, OrchestrationContext context) {
        try {
            runStateStore.markFinished(runId, context.attributes());
        } catch (Exception e) {
            log.warn("编排实例[{}] 标记成功失败: {}", runId, e.getMessage());
        }
    }

    private void safeMarkFailed(String runId, String failedNode, String error, OrchestrationContext context) {
        try {
            runStateStore.markFailed(runId, failedNode, error, context.attributes());
        } catch (Exception e) {
            log.warn("编排实例[{}] 标记失败失败: {}", runId, e.getMessage());
        }
    }
}
