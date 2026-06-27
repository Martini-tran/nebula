package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI流程边表
 * 描述节点间有向连接 {@code from_node -> to_node}，可附带 SpEL 条件表达式。
 * 对应 SDK 的 {@code com.nebula.common.ai.flow.FlowEdgeDefinition}。
 *
 * @author nebula
 */
@Data
@TableName("ai_flow_edge")
public class AiFlowEdge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 边ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属流程编码
     */
    private String flowCode;

    /**
     * 起点节点编码
     */
    private String fromNode;

    /**
     * 终点节点编码
     */
    private String toNode;

    /**
     * 条件表达式(SpEL)，空表示无条件直达
     */
    private String conditionExpr;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
