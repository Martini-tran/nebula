package com.nebula.common.ai.flow;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 流程边定义
 * 描述节点间有向连接 {@code fromNode -> toNode}，可附带 SpEL 条件表达式：仅当表达式对当前
 * 编排上下文求值为真时该边可达。{@code conditionExpr} 为空表示无条件直达。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class FlowEdgeDefinition {

    /**
     * 起点节点编码
     */
    private String fromNode;

    /**
     * 终点节点编码
     */
    private String toNode;

    /**
     * 条件表达式（SpEL），空表示无条件直达。
     * 以编排上下文为根对象求值，例如 {@code getString('intent') == 'series'}。
     */
    private String conditionExpr;

    /**
     * 排序号
     */
    private int sortNo;
}
