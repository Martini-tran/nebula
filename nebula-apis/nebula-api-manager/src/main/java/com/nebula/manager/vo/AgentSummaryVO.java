package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 智能体列表项（管理员端）
 *
 * @author nebula
 */
@Data
public class AgentSummaryVO {

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
