package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 空节点执行器（结构性节点占位）
 * 用于 {@code START / END / IF / JOIN} 这类「结构性」节点：它们不承载业务执行动作，
 * 仅作为图中的入口 / 出口 / 分流 / 汇聚锚点存在。DAG 内核靠「入度归零 + 出边 SpEL 条件」
 * 推进流程——分支判断落在 {@link FlowEdgeDefinition#getConditionExpr()} 出边上，汇聚靠入度，
 * 均与节点本身无关，因此这些节点的 {@link #execute} 是空操作。
 *
 * <p>没有它，{@link FlowGraphFactory#build} 会因「未找到节点类型的执行器」而拒绝构图。
 * 同一个类以不同 {@code type} 注册多个 Bean（见 {@code FlowAutoConfiguration}）即可覆盖多种结构类型，
 * 无需为每类结构节点各写一个空实现。
 *
 * @author nebula
 */
@Slf4j
public class NoOpNodeExecutor implements FlowNodeExecutor {

    /** 开始节点类型 */
    public static final String TYPE_START = "START";

    /** 结束节点类型 */
    public static final String TYPE_END = "END";

    /** 条件分支节点类型（分流靠出边条件，节点本身不执行） */
    public static final String TYPE_IF = "IF";

    /** 并行汇聚节点类型（汇聚靠入度归零，节点本身不执行） */
    public static final String TYPE_JOIN = "JOIN";

    private final String type;

    public NoOpNodeExecutor(String type) {
        this.type = type;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        // 结构性节点无执行动作：入口/出口/分流/汇聚均由图结构与出边条件承载
        log.debug("结构节点[{}] type={} 无执行动作，直接放行", node.getNodeCode(), type);
    }
}
