package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.statemachine.StateConfig;
import com.nebula.common.ai.orchestration.statemachine.StateMachineGraph;
import com.nebula.common.ai.orchestration.statemachine.StateNode;
import com.nebula.common.ai.orchestration.statemachine.StateTransition;
import com.nebula.common.ai.orchestration.statemachine.StateType;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 状态机图工厂
 * 把 {@code engine_type=STATE_MACHINE} 的 {@link FlowDefinition} 编译为 {@link StateMachineGraph}，
 * 对标 {@link FlowGraphFactory} 之于 DAG。复用同一套 {@link FlowNodeExecutor}（节点行为）与
 * {@link ConditionCompiler}（guard 编译），差异只在产物：本工厂产出<b>可成环</b>且携带 stateType/stateConfig
 * 的状态机图，并在 {@link StateMachineGraph.Builder#build()} 中做 Fail-Fast 编图校验（ENTRY=1、TERMINAL≥1）。
 *
 * <p>节点规范化与 DAG 一致：未显式指定模型档案的节点继承流程默认档案。
 * {@code stateConfig} 从 {@code node.getNodeConfig().get("stateConfig")} 解析（决策：状态配置全进 node_config）。
 *
 * @author nebula
 */
public class FlowStateMachineFactory {

    /**
     * {@code node_config} 中承载状态配置的子键
     */
    public static final String STATE_CONFIG_KEY = "stateConfig";

    private final Map<String, FlowNodeExecutor> executors;

    private final ConditionCompiler conditionCompiler;

    public FlowStateMachineFactory(List<FlowNodeExecutor> executors, ConditionCompiler conditionCompiler) {
        this.executors = executors == null ? Map.of()
                : executors.stream().collect(Collectors.toMap(FlowNodeExecutor::type, e -> e, (a, b) -> a));
        this.conditionCompiler = conditionCompiler;
    }

    /**
     * 构建状态机图
     *
     * @param def 流程定义（engine_type 应为 STATE_MACHINE）
     * @return 状态机图
     */
    public StateMachineGraph build(FlowDefinition def) {
        if (def == null || def.getFlowCode() == null || def.getFlowCode().isBlank()) {
            throw new OrchestrationException("流程定义或流程编码不能为空");
        }
        StateMachineGraph.Builder builder = StateMachineGraph.builder(def.getFlowCode())
                .maxTransitions(def.getMaxTransitions());

        List<FlowNodeDefinition> nodes = def.getNodes() == null ? List.of() : def.getNodes();
        // 保留键校验：业务节点 outputKey 不得占用系统保留前缀 __（构图期快速失败）
        ReservedKeyValidator.check(nodes);
        for (FlowNodeDefinition node : nodes) {
            if (node.getProfileCode() == null || node.getProfileCode().isBlank()) {
                node.setProfileCode(def.getDefaultProfileCode());
            }
            FlowNodeExecutor executor = executors.get(node.getNodeType());
            if (executor == null) {
                throw new OrchestrationException("未找到节点类型的执行器: " + node.getNodeType()
                        + "（节点 " + node.getNodeCode() + "）");
            }
            StateType type = StateType.of(node.getStateType());
            StateConfig config = resolveStateConfig(node);
            builder.state(new StateNode(node.getNodeCode(), type, config, ctx -> executor.execute(node, ctx)));
        }

        List<FlowEdgeDefinition> edges = def.getEdges() == null ? List.of() : def.getEdges();
        for (FlowEdgeDefinition edge : edges) {
            Predicate<OrchestrationContext> guard = conditionCompiler.compile(edge.getConditionExpr());
            builder.transition(new StateTransition(
                    edge.getFromNode(), edge.getToNode(), guard, edge.getEventName(), edge.getSortNo()));
        }
        return builder.build();
    }

    /**
     * 从节点配置解析状态配置：读 {@code node_config.stateConfig} 段，缺失退化为默认。
     */
    private StateConfig resolveStateConfig(FlowNodeDefinition node) {
        Map<String, Object> nodeConfig = node.getNodeConfig();
        if (nodeConfig == null) {
            return StateConfig.NONE;
        }
        return StateConfig.fromMap(nodeConfig.get(STATE_CONFIG_KEY));
    }
}
