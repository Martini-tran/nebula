package com.nebula.common.ai.orchestration.statemachine;

import com.nebula.common.ai.orchestration.OrchestrationContext;

import java.util.function.Predicate;

/**
 * 状态转移
 * 状态机的有向边 {@code from -> to}，携带 <b>guard</b>（守卫条件，复用 {@code ai_flow_edge.condition_expr}
 * 编译出的谓词）与可选 <b>event</b>（触发事件名，阶段 1 不参与裁决）。{@link #sortNo} 决定裁决顺序：
 * 一个状态成功后按 sortNo 升序逐条求值 guard，取首个为 true 的转移。
 *
 * <p>{@code guard} 为 null 表示无条件恒真（default 边），通常配最大 sortNo 放最后作为 else 分支。
 *
 * @author nebula
 */
public class StateTransition {

    private final String from;

    private final String to;

    private final Predicate<OrchestrationContext> guard;

    private final String eventName;

    private final int sortNo;

    public StateTransition(String from, String to, Predicate<OrchestrationContext> guard,
                           String eventName, int sortNo) {
        this.from = from;
        this.to = to;
        this.guard = guard;
        this.eventName = eventName;
        this.sortNo = sortNo;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public String eventName() {
        return eventName;
    }

    public int sortNo() {
        return sortNo;
    }

    /**
     * 是否为无条件 default 边（guard 为空）
     *
     * @return guard 为 null 时 true
     */
    public boolean isDefault() {
        return guard == null;
    }

    /**
     * 评估该转移的 guard 在当前上下文下是否成立
     *
     * @param ctx 编排共享上下文
     * @return default 边恒为 true；条件边取谓词结果
     */
    public boolean isSatisfied(OrchestrationContext ctx) {
        return guard == null || guard.test(ctx);
    }
}
