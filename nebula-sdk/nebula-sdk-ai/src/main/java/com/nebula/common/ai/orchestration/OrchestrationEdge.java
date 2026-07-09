package com.nebula.common.ai.orchestration;

import java.util.function.Predicate;

/**
 * 编排边
 * 描述节点间的有向连接 {@code from -> to}，可附带条件谓词：仅当 {@code condition} 对当前
 * {@link OrchestrationContext} 成立时该边才「可达」。条件边让同一张图既能表达 DAG 依赖，
 * 又能表达状态机式的条件分支。{@code condition} 为 null 表示无条件直达。
 *
 * @author nebula
 */
public class OrchestrationEdge {

    private final String from;

    private final String to;

    private final Predicate<OrchestrationContext> condition;

    public OrchestrationEdge(String from, String to, Predicate<OrchestrationContext> condition) {
        this.from = from;
        this.to = to;
        this.condition = condition;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    /**
     * 评估该边在当前上下文下是否可达
     *
     * @param ctx 编排共享上下文
     * @return 无条件边恒为 true；条件边取谓词结果
     */
    public boolean isSatisfied(OrchestrationContext ctx) {
        return condition == null || condition.test(ctx);
    }
}
