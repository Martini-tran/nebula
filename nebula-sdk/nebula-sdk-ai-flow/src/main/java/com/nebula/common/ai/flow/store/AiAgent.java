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
 * AI 智能体定义表
 * Agent 是对 Flow 编排图的一层封装：通过 {@code flowCode + flowVersion} 引用一张编排图，附带记忆策略 / IO 契约 /
 * 默认模型档案。多个 Agent 可引用<b>同一个 Flow</b>（1 Flow : N Agent）。对应 SDK 的
 * {@code com.nebula.common.ai.agent.AgentDefinition}。
 *
 * <p>{@code agentCode} 跨版本稳定（= 记忆隔离键），{@code (agentCode, version)} 联合唯一。
 *
 * @author nebula
 */
@Data
@TableName("ai_agent")
public class AiAgent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Agent 编码：跨版本稳定的逻辑标识（非全局唯一），= 记忆隔离键
     */
    private String agentCode;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 引用的编排图编码
     */
    private String flowCode;

    /**
     * 引用的编排图版本
     */
    private Integer flowVersion;

    /**
     * 输入契约 JSON Schema
     */
    private String inputSchema;

    /**
     * 输出契约 JSON Schema
     */
    private String outputSchema;

    /**
     * 记忆配置 JSON：enabled / 导入键 / 导出策略
     */
    private String memoryConfig;

    /**
     * 默认模型档案编码
     */
    private String defaultProfileCode;

    /**
     * Agent 定义版本，发布后不可变
     */
    private Integer version;

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
