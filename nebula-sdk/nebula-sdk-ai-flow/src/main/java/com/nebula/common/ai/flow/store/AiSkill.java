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
 * AI技能表
 * 把「成套的做事方法论 + 配套工具」沉淀为可复用资源，由 {@code skill_code} 被 Agent/节点引用。
 * 一个技能 = 元数据（编码/名称/描述）+ Markdown 指令正文（{@code instructions}）+ 绑定工具（{@code tool_codes}）。
 * 命中后指令以 {@code system} 消息注入模型上下文，绑定工具并入该次调用的工具白名单。
 *
 * <p>与相邻表的分工：{@code ai_prompt} 是单条提示词文本（无工具绑定）；{@code ai_tool} 是单个可执行函数
 * （代码定义、启动同步进表、只读）；本表是<b>纯 DB 配置</b>，后台改完即刻生效、无需发版。
 *
 * <p>{@code instructions} 内可写 <code>{{变量名}}</code> 占位符，由编排上下文变量池渲染；
 * {@code tool_codes}/{@code mcp_server_codes} 为 JSON 数组字符串（序列化在服务层处理）。
 *
 * @author nebula
 */
@Data
@TableName("ai_skill")
public class AiSkill implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 技能ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 技能编码，全局唯一，被 Agent/节点引用
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
     * 绑定的工具编码数组（JSON数组字符串；序列化在服务层处理）
     */
    private String toolCodes;

    /**
     * 绑定的 MCP 服务器编码数组（JSON数组字符串；序列化在服务层处理）
     */
    private String mcpServerCodes;

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
