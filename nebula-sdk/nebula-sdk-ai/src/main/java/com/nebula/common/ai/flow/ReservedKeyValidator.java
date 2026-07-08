package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.ContextKeys;
import com.nebula.common.ai.orchestration.OrchestrationException;

import java.util.ArrayList;
import java.util.List;

/**
 * 保留键校验器
 * 构图期静态校验：业务节点写回上下文的键（{@code outputKey}）不得占用系统保留前缀 {@code __}
 * （见 {@link ContextKeys#RESERVED_PREFIX}）。否则业务键与系统约定键相撞后会在同一平坦上下文里
 * 静默互相覆盖——例如某节点 {@code outputKey="__output"} 会踩掉 END 的最终结果。
 *
 * <p>由 {@link FlowGraphFactory#build} 与 {@link FlowStateMachineFactory#build} 在编图前各调一趟，
 * 命中即抛 {@link OrchestrationException}，让非法配置在构图/保存期就快速失败，而非运行到一半才炸。
 *
 * <p>运行期动态展开键（如 PROMPT 节点 {@code outputMode=JSON} 把模型输出逐键写回）无法在构图期静态
 * 得知，不在本校验范围——由 END 出参 schema 兜底 + 文档约定规避。
 *
 * @author nebula
 */
public final class ReservedKeyValidator {

    private ReservedKeyValidator() {
    }

    /**
     * 校验节点列表：任一节点 {@code outputKey} 以 {@code __} 开头即抛异常（聚合所有违规一次报出）。
     *
     * @param nodes 节点列表（可空）
     * @throws OrchestrationException 存在保留前缀违规时
     */
    public static void check(List<FlowNodeDefinition> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        List<String> violations = new ArrayList<>();
        for (FlowNodeDefinition node : nodes) {
            String outputKey = node.getOutputKey();
            if (ContextKeys.isReserved(outputKey)) {
                violations.add("节点[" + node.getNodeCode() + "]的 outputKey=\"" + outputKey
                        + "\" 占用了系统保留前缀 \"" + ContextKeys.RESERVED_PREFIX + "\"");
            }
        }
        if (!violations.isEmpty()) {
            throw new OrchestrationException("流程配置非法（保留键冲突）：" + String.join("；", violations));
        }
    }
}
