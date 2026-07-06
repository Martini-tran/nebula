package com.nebula.common.ai.orchestration.statemachine;

import com.nebula.common.ai.orchestration.OrchestrationNode;

/**
 * 状态节点
 * 状态机中的一个状态：{@code code} 为状态编码（= 流程节点编码），{@link #behavior} 是"做什么"（复用
 * DAG 侧的 {@link OrchestrationNode} 函数式抽象，从而共用同一套 {@code FlowNodeExecutor}），
 * {@link #type} 标注入口/普通/终态，{@link #config} 承载重试/超时/错误转移。
 *
 * <p>与 DAG 的 {@code OrchestrationNode} 的区别：状态机节点是有身份（code/type/config）的对象，
 * 内核执行单个状态时需读 {@link #config} 决定失败处置，而 DAG 只需一个匿名可执行单元。
 *
 * @author nebula
 */
public class StateNode {

    private final String code;

    private final StateType type;

    private final StateConfig config;

    private final OrchestrationNode behavior;

    public StateNode(String code, StateType type, StateConfig config, OrchestrationNode behavior) {
        this.code = code;
        this.type = type == null ? StateType.NORMAL : type;
        this.config = config == null ? StateConfig.NONE : config;
        this.behavior = behavior;
    }

    public String code() {
        return code;
    }

    public StateType type() {
        return type;
    }

    public StateConfig config() {
        return config;
    }

    public OrchestrationNode behavior() {
        return behavior;
    }

    public boolean isEntry() {
        return type == StateType.ENTRY;
    }

    public boolean isTerminal() {
        return type == StateType.TERMINAL;
    }
}
