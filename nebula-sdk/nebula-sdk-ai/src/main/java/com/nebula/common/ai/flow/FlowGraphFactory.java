package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.OrchestrationGraph;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 * @author nebula
 */
public class FlowGraphFactory {

    private final Map<String, FlowNodeExecutor> executors;

    private final ConditionCompiler conditionCompiler;

    public FlowGraphFactory(List<FlowNodeExecutor> executors, ConditionCompiler conditionCompiler) {
        this.executors = executors == null ? Map.of()
                : executors.stream().collect(Collectors.toMap(FlowNodeExecutor::type, e -> e, (a, b) -> a));
        this.conditionCompiler = conditionCompiler;
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
        OrchestrationGraph.Builder builder = OrchestrationGraph.builder(def.getFlowCode());

        List<FlowNodeDefinition> nodes = def.getNodes() == null ? List.of() : def.getNodes().stream()
                .sorted(Comparator.comparingInt(FlowNodeDefinition::getSortNo))
                .toList();
        for (FlowNodeDefinition node : nodes) {
            if (node.getProfileCode() == null || node.getProfileCode().isBlank()) {
                node.setProfileCode(def.getDefaultProfileCode());
            }
            FlowNodeExecutor executor = executors.get(node.getNodeType());
            if (executor == null) {
                throw new OrchestrationException("未找到节点类型的执行器: " + node.getNodeType()
                        + "（节点 " + node.getNodeCode() + "）");
            }
            builder.node(node.getNodeCode(), ctx -> executor.execute(node, ctx));
        }

        List<FlowEdgeDefinition> edges = def.getEdges() == null ? List.of() : def.getEdges().stream()
                .sorted(Comparator.comparingInt(FlowEdgeDefinition::getSortNo))
                .toList();
        for (FlowEdgeDefinition edge : edges) {
            Predicate<OrchestrationContext> condition = conditionCompiler.compile(edge.getConditionExpr());
            builder.edge(edge.getFromNode(), edge.getToNode(), condition);
        }
        return builder.build();
    }
}
