package com.nebula.manager.dto;

import lombok.Data;

/**
 * AI 智能体创建/更新请求
 * 维护 {@code ai_agent} 定义：引用哪张编排图（flowCode/flowVersion）+ IO 契约 + 记忆配置 + 默认模型档案。
 *
 * @author nebula
 */
@Data
public class AgentSaveRequest {

    /**
     * Agent 编码（跨版本稳定的逻辑标识 = 记忆隔离键；创建后不建议改）
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
     * 记忆配置 JSON：enabled / 导入键 / 导出策略（Replace/Append/Summary）
     */
    private String memoryConfig;

    /**
     * 默认模型档案编码
     */
    private String defaultProfileCode;

    /**
     * Agent 定义版本（缺省 1）
     */
    private Integer version;

    /**
     * 状态：0=停用 1=启用（缺省 1）
     */
    private Integer status;
}
