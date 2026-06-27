package com.nebula.blog.vo.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI流程列表行（管理员端）
 *
 * @author nebula
 */
@Data
public class FlowSummaryVO {

    /**
     * 流程编码
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
     * 版本号
     */
    private Integer version;

    /**
     * 默认模型档案编码
     */
    private String defaultProfileCode;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 节点数
     */
    private Integer nodeCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
