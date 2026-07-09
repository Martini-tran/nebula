package com.nebula.common.ai.agent;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Agent 静态定义
 * 对 Flow 编排图的一层封装：一个 Agent 通过 {@code flowCode + flowVersion} 引用一张编排图，
 * 并附带自己的记忆策略 / IO 契约 / 默认模型档案。多个 Agent 可引用<b>同一个 Flow</b>（1 Flow : N Agent），
 * Flow 保持纯编排结构、可被复用。对应 {@code ai_agent} 表。
 *
 * <p>{@code agentCode} 是<b>跨版本稳定</b>的逻辑标识（非全局唯一），同时是<b>长期记忆隔离键</b>——
 * 同 {@code agentCode} 的各版本共享一份长期记忆；{@code (agentCode, version)} 联合唯一。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class AgentDefinition {

    /**
     * Agent 编码：跨版本稳定的逻辑标识，同时是记忆隔离键
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
     * 引用的编排图版本（可换绑做灰度）
     */
    private int flowVersion = 1;

    /**
     * 输入契约 JSON Schema（可空）
     */
    private String inputSchema;

    /**
     * 输出契约 JSON Schema（可空）
     */
    private String outputSchema;

    /**
     * 记忆配置 JSON：{@code enabled} / 导入键 / 导出策略（Replace/Append/Summary）。
     * 由 {@link AgentMemoryPolicy#parse(String)} 解析。
     */
    private String memoryConfig;

    /**
     * 默认模型档案编码：模型档案三层优先级的中间层（节点级 &gt; Agent 默认 &gt; Flow 默认）。
     * 编图前灌入 {@code FlowDefinition.defaultProfileCode} 参与定档。
     */
    private String defaultProfileCode;

    /**
     * Agent 定义版本，发布后不可变
     */
    private int version = 1;

    /**
     * 状态：0=停用 1=启用。停用只挡"新建实例"入口，不影响已创建的运行中实例。
     */
    private int status = 1;

    /**
     * 是否启用（status==1）
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return status == 1;
    }
}
