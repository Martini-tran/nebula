package com.nebula.common.ai.flow;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程定义
 * 一个流程对应一张编排图（DAG）：由若干节点与有向（可带条件）边构成。{@code flowCode} 既是流程唯一
 * 编码，也用作编排图编码与编排器记忆的归属编码。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class FlowDefinition {

    /**
     * 流程编码，全局唯一
     */
    private String flowCode;

    /**
     * 流程名称
     */
    private String name;

    /**
     * 流程描述
     */
    private String description;

    /**
     * 版本号，用于缓存键与灰度
     */
    private int version = 1;

    /**
     * 流程默认模型档案编码，节点未指定档案时使用
     */
    private String defaultProfileCode;

    /**
     * 执行内核：{@code DAG}（默认，拓扑推进）| {@code STATE_MACHINE}（单点状态推进，可回跳/成环）。
     * 对应 {@code ai_flow.engine_type} 列，由引擎据此选择编排内核，对上层透明。
     */
    private String engineType = "DAG";

    /**
     * 状态机全局转移次数上限，防死循环（对应 {@code ai_flow.max_transitions}）。
     * 仅 {@code engineType=STATE_MACHINE} 使用；不同流程循环深度差异大，故 per-Flow 可配。
     */
    private int maxTransitions = 100;

    /**
     * 节点列表
     */
    private List<FlowNodeDefinition> nodes = new ArrayList<>();

    /**
     * 边列表
     */
    private List<FlowEdgeDefinition> edges = new ArrayList<>();
}
