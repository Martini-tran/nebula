package com.nebula.common.ai.skill;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能定义（运行期视图）
 *
 * <p>技能是「可复用的能力说明书」，填补 {@code 提示词}（纯文本片段）与 {@code 工具}（可执行函数）之间的空白：
 * 一个技能 = 元数据（编码/名称/描述）+ Markdown 指令正文 + 可选绑定的工具白名单。命中后，
 * {@code instructions} 以 {@code system} 消息注入模型上下文，{@code toolCodes} 并入该次调用的工具白名单，
 * 从而让模型「知道怎么做」并「拿到做这件事需要的工具」。
 *
 * <p><b>与相邻概念的分工</b>：
 * <ul>
 *   <li>{@code ai_prompt}：单条提示词文本，无工具绑定；</li>
 *   <li>{@code ai_tool}：单个可执行函数，代码定义、启动同步进表；</li>
 *   <li>本类：成套的做事方法论 + 配套工具，纯 DB 配置、后台可写，不需发版即可新增能力。</li>
 * </ul>
 *
 * <p><b>触发方式</b>由 {@code triggerType} 决定：{@code MANUAL} 仅在被显式引用（Agent 级
 * {@code skillCodes} 或节点级 {@code nodeConfig.skillCodes}）时装载；{@code AUTO} 表示该技能在
 * 所属范围内始终装载。当前两种都需先被引用，{@code AUTO} 语义预留给后续「按 description 语义匹配」。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class SkillDefinition {

    /**
     * 触发方式：自动装载
     */
    public static final String TRIGGER_AUTO = "AUTO";

    /**
     * 触发方式：显式引用才装载
     */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /**
     * 技能编码，全局唯一，被 Agent/节点按编码引用
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
     * 指令正文（Markdown），命中后作为 system 消息注入；支持 <code>{{变量名}}</code> 占位符，
     * 由编排上下文变量池渲染。
     */
    private String instructions;

    /**
     * 触发方式：AUTO/MANUAL
     */
    private String triggerType = TRIGGER_MANUAL;

    /**
     * 绑定的工具编码列表，命中后并入该次调用的工具白名单
     */
    private List<String> toolCodes = new ArrayList<>();

    /**
     * 绑定的 MCP 服务器编码列表（预留：MCP 工具接入闭环后按此拉起）
     */
    private List<String> mcpServerCodes = new ArrayList<>();

    /**
     * 排序号，决定多技能注入时的 system 消息先后顺序
     */
    private Integer sortNo = 0;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status = 1;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否启用
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /**
     * 指令正文是否有实际内容
     *
     * @return 有正文返回 true
     */
    public boolean hasInstructions() {
        return instructions != null && !instructions.isBlank();
    }
}
