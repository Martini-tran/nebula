package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 智能体定义详情（管理员端，含 IO 契约 / 记忆配置全字段，用于编辑回显）
 * 列表用精简的 {@link AgentSummaryVO}（不含大 TEXT 字段）；详情/编辑用本 VO。
 *
 * @author nebula
 */
@Data
public class AgentDetailVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * Agent 编码
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
     * Agent 定义版本
     */
    private Integer version;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
