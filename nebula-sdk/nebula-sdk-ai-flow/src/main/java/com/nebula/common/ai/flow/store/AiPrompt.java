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
 * AI提示词表
 * 把散落在流程节点配置里的提示词文本沉淀为可复用资源，由 {@code prompt_code} 被流程/节点引用。
 * {@code content} 内可写 <code>{{变量名}}</code> 占位符，占位符的名称/类型/是否必填由 {@code variables}
 * 以 JSON 数组声明（实体只承载原始列值，序列化在服务层处理）。
 *
 * @author nebula
 */
@Data
@TableName("ai_prompt")
public class AiPrompt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提示词ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 提示词编码，全局唯一，被流程/节点引用
     */
    private String promptCode;

    /**
     * 提示词名称
     */
    private String name;

    /**
     * 消息角色：system=系统设定 user=用户输入 assistant=助手示例
     */
    private String role;

    /**
     * 提示词正文，支持 <code>{{变量名}}</code> 占位符
     */
    private String content;

    /**
     * 变量声明数组（JSON数组字符串；序列化在服务层处理）
     */
    private String variables;

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
