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
 * AI流程定义表
 * 一个流程对应一张编排图（DAG），由若干节点（ai_flow_node）与有向边（ai_flow_edge）构成。
 * 对应 SDK 的 {@code com.nebula.common.ai.flow.FlowDefinition}。
 *
 * @author nebula
 */
@Data
@TableName("ai_flow")
public class AiFlow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流程ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

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
    private Integer version;

    /**
     * 默认模型档案编码
     */
    private String defaultProfileCode;

    /**
     * 执行内核：DAG | STATE_MACHINE
     */
    private String engineType;

    /**
     * 状态机全局转移次数上限，防死循环；per-Flow 可配
     */
    private Integer maxTransitions;

    /**
     * 递归子 Agent 最大深度，防无限递归
     */
    private Integer maxAgentDepth;

    /**
     * 流程级回调 URL：实例到终态后 POST 产物到此（INSTANCE_SUCCESS/FAILED），空则不回调。见 docs/编排回调Webhook设计.md
     */
    private String webhookUrl;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

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
