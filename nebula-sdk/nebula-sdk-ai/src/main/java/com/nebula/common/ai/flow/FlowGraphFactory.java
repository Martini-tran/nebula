package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.OrchestrationGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 流程图工厂
 * 把 {@link FlowDefinition} 编译为可执行的 {@link OrchestrationGraph}：按节点类型选取
 * {@link FlowNodeExecutor} 把每个节点包装为编排节点，按边的 SpEL 条件编译为条件边，最终交由
 * {@link OrchestrationGraph.Builder} 完成引用合法性与无环校验。
 *
 * <p>构图时对节点做一次规范化：未显式指定模型档案的节点继承流程默认档案（{@code defaultProfileCode}）。
 *
 * <p><b>LOOP 内嵌子图</b>：{@code nodeType=LOOP} 是画布容器，其 {@code nodeConfig.members} 圈定的成员节点
 * <b>不进主图</b>（否则会被主图执行一次 + LOOP 再执行 N 次，重复），而是编成一张子图 {@link LoopBody} 由
 * {@link LoopNodeExecutor} 内部循环。构图时按层递归 {@link #compileScope}：识别本层直接子 LOOP、剔除其成员、
 * 重写进出容器的边（塌缩到 LOOP 节点）、为每个子 LOOP 递归编译子图。嵌套 LOOP（内层是外层的成员）由递归天然支持。
 *
 * @author nebula
 */
public class FlowGraphFactory {

    private final Map<String, FlowNodeExecutor> executors;

    private final ConditionCompiler conditionCompiler;

    /**
     * 循环驱动器，可空。为空时含 LOOP 节点的流程构图报错（未装配 LOOP 支持）。
     */
    private final LoopNodeExecutor loopExecutor;

    public FlowGraphFactory(List<FlowNodeExecutor> executors, ConditionCompiler conditionCompiler) {
        this(executors, conditionCompiler, null);
    }

    public FlowGraphFactory(List<FlowNodeExecutor> executors, ConditionCompiler conditionCompiler,
                            LoopNodeExecutor loopExecutor) {
        this.executors = executors == null ? Map.of()
                : executors.stream().collect(Collectors.toMap(FlowNodeExecutor::type, e -> e, (a, b) -> a));
        this.conditionCompiler = conditionCompiler;
        this.loopExecutor = loopExecutor;
    }

    /**
     * 构建编排图
     *
     * @param def 流程定义
     * @return 编排图
     */
    public OrchestrationGraph build(FlowDefinition def) {
        if (def == null || def.getFlowCode() == null || def.getFlowCode().isBlank()) {
            throw new OrchestrationException("流程定义或流程编码不能为空");
        }
        List<FlowNodeDefinition> nodes = def.getNodes() == null ? List.of() : def.getNodes().stream()
                .sorted(Comparator.comparingInt(FlowNodeDefinition::getSortNo))
                .toList();
        // 保留键校验：业务节点 outputKey 不得占用系统保留前缀 __（构图期快速失败）
        ReservedKeyValidator.check(nodes);
        // 一次性规范化：未指定档案的节点继承流程默认档案（含成员节点，其在子图里同样需要）
        for (FlowNodeDefinition node : nodes) {
            if (node.getProfileCode() == null || node.getProfileCode().isBlank()) {
                node.setProfileCode(def.getDefaultProfileCode());
            }
        }
        Map<String, FlowNodeDefinition> nodeByCode = new HashMap<>();
        for (FlowNodeDefinition node : nodes) {
            nodeByCode.put(node.getNodeCode(), node);
        }
        List<FlowEdgeDefinition> edges = def.getEdges() == null ? List.of() : def.getEdges().stream()
                .sorted(Comparator.comparingInt(FlowEdgeDefinition::getSortNo))
                .toList();

        // 所有成员节点（任一 LOOP 的成员）—— 顶层 scope = 全部节点去掉「被任何 LOOP 当成员」的节点
        Set<String> allMembers = collectAllMembers(nodes);
        Set<String> topScope = new LinkedHashSet<>();
        for (FlowNodeDefinition node : nodes) {
            if (!allMembers.contains(node.getNodeCode())) {
                topScope.add(node.getNodeCode());
            }
        }
        return compileScope(def.getFlowCode(), topScope, nodeByCode, edges);
    }

    /**
     * 编译一个作用域（主图或某 LOOP 的成员子图）为编排图。
     *
     * <p>作用域 = 一批节点编码 + 全量边。本层直接子 LOOP = 作用域内 {@code nodeType=LOOP} 的节点
     * （它们的成员已从本作用域剔除，故成员不会出现在 scope 里；嵌套的更内层 LOOP 由递归处理）。
     *
     * @param code      图编码（主图=flowCode，子图=flowCode:loopCode）
     * @param scope     本作用域的节点编码集合（已剔除下层成员）
     * @param nodeByCode 全部节点索引
     * @param allEdges  全量边（本层只取两端都与本作用域相关的边并按需塌缩）
     * @return 本作用域编排图
     */
    private OrchestrationGraph compileScope(String code, Set<String> scope,
                                            Map<String, FlowNodeDefinition> nodeByCode,
                                            List<FlowEdgeDefinition> allEdges) {
        // 本层直接子 LOOP → 其成员集合；并建立 member→loop 反查（塌缩边用）
        Map<String, Set<String>> loopMembers = new HashMap<>();
        Map<String, String> memberToLoop = new HashMap<>();
        for (String nodeCode : scope) {
            FlowNodeDefinition node = nodeByCode.get(nodeCode);
            if (node != null && LoopNodeExecutor.TYPE.equalsIgnoreCase(node.getNodeType())) {
                Set<String> members = readMembers(node);
                loopMembers.put(nodeCode, members);
                for (String m : members) {
                    memberToLoop.put(m, nodeCode);
                }
            }
        }

        OrchestrationGraph.Builder builder = OrchestrationGraph.builder(code);

        // 登记本层节点：非成员节点 + LOOP 节点本身（成员归各自子图）
        for (String nodeCode : scope) {
            FlowNodeDefinition node = nodeByCode.get(nodeCode);
            if (node == null) {
                throw new OrchestrationException("作用域引用了不存在的节点: " + nodeCode);
            }
            if (LoopNodeExecutor.TYPE.equalsIgnoreCase(node.getNodeType())) {
                if (loopExecutor == null) {
                    throw new OrchestrationException("流程含 LOOP 节点[" + nodeCode
                            + "]但未装配 LoopNodeExecutor（循环支持未启用）");
                }
                LoopBody body = compileLoopBody(code, node, loopMembers.get(nodeCode), nodeByCode, allEdges);
                builder.node(nodeCode, ctx -> loopExecutor.execute(node, ctx, body));
            } else {
                FlowNodeExecutor executor = executors.get(node.getNodeType());
                if (executor == null) {
                    throw new OrchestrationException("未找到节点类型的执行器: " + node.getNodeType()
                            + "（节点 " + node.getNodeCode() + "）");
                }
                builder.node(nodeCode, ctx -> executor.execute(node, ctx));
            }
        }

        // 边重写：端点若是本层某子 LOOP 的成员则塌缩到该 LOOP；两端都不在本层 scope 的边跳过；
        // 塌缩后自环丢弃；两端同属一个子 LOOP（内部边）不进本层（归子图）
        for (FlowEdgeDefinition edge : allEdges) {
            String from = collapse(edge.getFromNode(), scope, memberToLoop);
            String to = collapse(edge.getToNode(), scope, memberToLoop);
            if (from == null || to == null) {
                continue; // 有一端不属于本层（属于更外层或另一子图）
            }
            if (from.equals(to)) {
                continue; // 塌缩后自环（含同一子 LOOP 内部边）丢弃
            }
            Predicate<OrchestrationContext> condition = conditionCompiler.compile(edge.getConditionExpr());
            builder.edge(from, to, condition);
        }
        return builder.build();
    }

    /**
     * 把一个节点端点塌缩到本作用域的可见节点：
     * 本层直接节点 → 原样；本层某子 LOOP 的成员 → 该 LOOP 节点；两者皆非 → null（不属于本层）。
     */
    private String collapse(String nodeCode, Set<String> scope, Map<String, String> memberToLoop) {
        if (scope.contains(nodeCode)) {
            return nodeCode;
        }
        String loop = memberToLoop.get(nodeCode);
        return loop != null && scope.contains(loop) ? loop : null;
    }

    /**
     * 编译一个 LOOP 节点的成员子图为 {@link LoopBody}（递归——成员里可含更内层 LOOP）。
     * 子图作用域 = 本 LOOP 的成员去掉「被本 LOOP 内更内层 LOOP 当成员」的节点。子图 orchestrator 无状态存储。
     */
    private LoopBody compileLoopBody(String parentCode, FlowNodeDefinition loopNode, Set<String> members,
                                     Map<String, FlowNodeDefinition> nodeByCode, List<FlowEdgeDefinition> allEdges) {
        if (members == null || members.isEmpty()) {
            throw new OrchestrationException("LOOP 节点[" + loopNode.getNodeCode() + "]未圈定成员（nodeConfig.members 为空）");
        }
        // 子图作用域：本 LOOP 成员，剔除更内层 LOOP 的成员（内层成员归内层子图）
        Set<String> innerMembers = new HashSet<>();
        for (String m : members) {
            FlowNodeDefinition mn = nodeByCode.get(m);
            if (mn != null && LoopNodeExecutor.TYPE.equalsIgnoreCase(mn.getNodeType())) {
                innerMembers.addAll(readMembers(mn));
            }
        }
        Set<String> scope = new LinkedHashSet<>();
        for (String m : members) {
            if (!innerMembers.contains(m)) {
                scope.add(m);
            }
        }
        String bodyCode = parentCode + ":" + loopNode.getNodeCode();
        OrchestrationGraph graph = compileScope(bodyCode, scope, nodeByCode, allEdges);
        return new LoopBody(graph, new DagOrchestrator());
    }

    /**
     * 收集全部 LOOP 节点的成员并集（用于顶层 scope 剔除）
     */
    private Set<String> collectAllMembers(List<FlowNodeDefinition> nodes) {
        Set<String> all = new HashSet<>();
        for (FlowNodeDefinition node : nodes) {
            if (LoopNodeExecutor.TYPE.equalsIgnoreCase(node.getNodeType())) {
                all.addAll(readMembers(node));
            }
        }
        return all;
    }

    /**
     * 读取 LOOP 节点的成员编码集合（{@code nodeConfig.members}，List&lt;String&gt;）
     */
    @SuppressWarnings("unchecked")
    private Set<String> readMembers(FlowNodeDefinition node) {
        Object raw = node.getNodeConfig() == null ? null : node.getNodeConfig().get("members");
        Set<String> members = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    members.add(String.valueOf(o));
                }
            }
        }
        return members;
    }
}
