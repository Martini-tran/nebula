package com.nebula.common.ai.flow;

/**
 * 工具允许出现的调用域。
 *
 * <p>生成控制面的工具与正式流程节点工具必须显式隔离，避免流程定义通过 TOOL 节点间接调用提交、
 * 真实试跑等高权限操作。
 *
 * @author nebula
 */
public enum InvocationScope {

    /** 流程生成 Harness 的模型工具调用。 */
    COPILOT_TOOL,

    /** 已发布流程中的 TOOL / AGENT_REACT 节点调用。 */
    FLOW_NODE
}
