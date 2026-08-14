package com.nebula.manager.dto;

import lombok.Data;

import java.util.List;

/**
 * AI技能保存请求（创建/更新共用）
 *
 * @author nebula
 */
@Data
public class SkillSaveRequest {

    /**
     * 技能编码，全局唯一（创建必填；更新时不可变更）
     */
    private String skillCode;

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述，用于人工挑选与后续语义匹配
     */
    private String description;

    /**
     * 指令正文（Markdown），命中后作为 system 消息注入；支持 <code>{{变量名}}</code> 占位符
     */
    private String instructions;

    /**
     * 触发方式：AUTO=范围内始终装载 MANUAL=被显式引用才装载
     */
    private String triggerType;

    /**
     * 绑定的工具编码列表，命中后并入该次调用的工具白名单
     */
    private List<String> toolCodes;

    /**
     * 绑定的 MCP 服务器编码列表
     */
    private List<String> mcpServerCodes;

    /**
     * 排序号，决定多技能注入时的 system 消息先后顺序
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
}
