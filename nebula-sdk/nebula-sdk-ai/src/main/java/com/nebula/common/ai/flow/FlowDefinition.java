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
     * 节点列表
     */
    private List<FlowNodeDefinition> nodes = new ArrayList<>();

    /**
     * 边列表
     */
    private List<FlowEdgeDefinition> edges = new ArrayList<>();
}
