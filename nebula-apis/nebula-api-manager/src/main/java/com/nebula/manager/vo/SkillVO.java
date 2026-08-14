package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI技能行（管理员端）
 *
 * @author nebula
 */
@Data
public class SkillVO {

    /**
     * 技能ID
     */
    private Long id;

    /**
     * 技能编码
     */
    private String skillCode;

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述
     */
    private String description;

    /**
     * 指令正文（Markdown）
     */
    private String instructions;

    /**
     * 触发方式：AUTO/MANUAL
     */
    private String triggerType;

    /**
     * 绑定的工具编码列表
     */
    private List<String> toolCodes;

    /**
     * 绑定的 MCP 服务器编码列表
     */
    private List<String> mcpServerCodes;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
