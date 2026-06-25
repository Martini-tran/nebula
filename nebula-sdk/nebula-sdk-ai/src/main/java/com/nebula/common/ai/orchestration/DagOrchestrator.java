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
 * @author nebula
 */
@Slf4j
public class DagOrchestrator implements Orchestrator {

    @Override
    public OrchestrationContext run(OrchestrationGraph graph, OrchestrationContext ctx) {
        if (graph == null) {
            throw new OrchestrationException("编排图不能为空");
        }
        OrchestrationContext context = ctx == null ? new OrchestrationContext() : ctx;
        List<String> order = topologicalOrder(graph);
        Set<String> executed = new HashSet<>();

        for (String id : order) {
            if (!isReachable(graph, id, executed, context)) {
                log.info("编排[{}] 跳过节点[{}]（条件不可达）", graph.code(), id);
                continue;
            }
            log.info("编排[{}] 执行节点[{}]", graph.code(), id);
            try {
                graph.node(id).execute(context);
            } catch (RuntimeException e) {
                log.error("编排[{}] 节点[{}]执行失败: {}", graph.code(), id, e.getMessage());
                throw e;
            }
            executed.add(id);
            context.recordResult(id, Boolean.TRUE);
            log.info("编排[{}] 完成节点[{}]", graph.code(), id);
        }
        return context;
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
}
