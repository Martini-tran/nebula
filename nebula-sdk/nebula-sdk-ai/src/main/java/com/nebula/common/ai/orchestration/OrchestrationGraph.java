package com.nebula.common.ai.orchestration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 编排图（DAG）
 * 由若干命名节点与有向（可带条件）边构成的有向无环图。通过{@link Builder}构建，构建时即校验
 * 边引用合法与无环，使非法编排尽早失败。节点顺序与边声明顺序均保持稳定，便于可预期的拓扑执行。
 *
 * @author nebula
 */
public class OrchestrationGraph {

    /**
     * 编排流程编码（可用作编排器记忆的归属编码）
     */
    private final String code;

    private final Map<String, OrchestrationNode> nodes;

    private final List<OrchestrationEdge> edges;

    private OrchestrationGraph(String code, Map<String, OrchestrationNode> nodes, List<OrchestrationEdge> edges) {
        this.code = code;
        this.nodes = nodes;
        this.edges = edges;
    }

    public String code() {
        return code;
    }

    /**
     * 获取节点
     *
     * @param id 节点ID
     * @return 节点，不存在返回 null
     */
    public OrchestrationNode node(String id) {
        return nodes.get(id);
    }

    /**
     * 全部节点ID（保持声明顺序）
     *
     * @return 节点ID集合
     */
    public Set<String> nodeIds() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    /**
     * 全部边
     *
     * @return 边列表
     */
    public List<OrchestrationEdge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * 指定节点的出边
     *
     * @param id 节点ID
     * @return 以该节点为起点的边
     */
    public List<OrchestrationEdge> outEdges(String id) {
        List<OrchestrationEdge> result = new ArrayList<>();
        for (OrchestrationEdge edge : edges) {
            if (edge.from().equals(id)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * 指定节点的入边
     *
     * @param id 节点ID
     * @return 以该节点为终点的边
     */
    public List<OrchestrationEdge> inEdges(String id) {
        List<OrchestrationEdge> result = new ArrayList<>();
        for (OrchestrationEdge edge : edges) {
            if (edge.to().equals(id)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * 根节点（入度为 0，编排的起点）
     *
     * @return 根节点ID列表（保持声明顺序）
     */
    public List<String> roots() {
        List<String> result = new ArrayList<>();
        for (String id : nodes.keySet()) {
            if (inEdges(id).isEmpty()) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * 创建图构建器
     *
     * @param code 编排流程编码
     * @return 构建器
     */
    public static Builder builder(String code) {
        return new Builder(code);
    }

    /**
     * 编排图构建器
     */
    public static class Builder {

        private final String code;

        private final Map<String, OrchestrationNode> nodes = new LinkedHashMap<>();

        private final List<OrchestrationEdge> edges = new ArrayList<>();

        private Builder(String code) {
            this.code = code;
        }

        /**
         * 登记一个节点
         *
         * @param id   节点ID（非空、不重复）
         * @param node 节点行为
         * @return 当前构建器
         */
        public Builder node(String id, OrchestrationNode node) {
            if (id == null || id.isEmpty()) {
                throw new OrchestrationException("节点ID不能为空");
            }
            if (node == null) {
                throw new OrchestrationException("节点[" + id + "]行为不能为空");
            }
            if (nodes.containsKey(id)) {
                throw new OrchestrationException("节点ID重复: " + id);
            }
            nodes.put(id, node);
            return this;
        }

        /**
         * 登记一条无条件边
         *
         * @param from 起点节点ID
         * @param to   终点节点ID
         * @return 当前构建器
         */
        public Builder edge(String from, String to) {
            return edge(from, to, null);
        }

        /**
         * 登记一条条件边
         *
         * @param from      起点节点ID
         * @param to        终点节点ID
         * @param condition 条件谓词（null 表示无条件直达）
         * @return 当前构建器
         */
        public Builder edge(String from, String to, Predicate<OrchestrationContext> condition) {
            edges.add(new OrchestrationEdge(from, to, condition));
            return this;
        }

        /**
         * 构建并校验图：边引用必须指向已登记节点，且整图无环
         *
         * @return 编排图
         */
        public OrchestrationGraph build() {
            for (OrchestrationEdge edge : edges) {
                if (!nodes.containsKey(edge.from())) {
                    throw new OrchestrationException("边引用了未登记的起点节点: " + edge.from());
                }
                if (!nodes.containsKey(edge.to())) {
                    throw new OrchestrationException("边引用了未登记的终点节点: " + edge.to());
                }
            }
            OrchestrationGraph graph = new OrchestrationGraph(
                    code, new LinkedHashMap<>(nodes), new ArrayList<>(edges));
            graph.assertAcyclic();
            return graph;
        }
    }

    /**
     * Kahn 算法校验无环，存在环则抛出异常
     */
    private void assertAcyclic() {
        Map<String, Integer> indegree = new HashMap<>();
        for (String id : nodes.keySet()) {
            indegree.put(id, 0);
        }
        for (OrchestrationEdge edge : edges) {
            indegree.merge(edge.to(), 1, Integer::sum);
        }
        Deque<String> queue = new ArrayDeque<>();
        for (String id : nodes.keySet()) {
            if (indegree.get(id) == 0) {
                queue.add(id);
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (OrchestrationEdge edge : outEdges(current)) {
                if (indegree.merge(edge.to(), -1, Integer::sum) == 0) {
                    queue.add(edge.to());
                }
            }
        }
        if (visited != nodes.size()) {
            throw new OrchestrationException("编排图存在环，无法拓扑执行");
        }
    }
}
