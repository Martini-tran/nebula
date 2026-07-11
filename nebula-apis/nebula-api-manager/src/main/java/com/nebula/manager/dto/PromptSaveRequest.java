package com.nebula.manager.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI提示词保存请求（创建/更新共用）
 *
 * @author nebula
 */
@Data
public class PromptSaveRequest {

    /**
     * 提示词编码，全局唯一（创建必填；更新时不可变更）
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
     * 变量声明数组，元素形如
     * <code>{"name":"topic","type":"string","required":true,"description":"主题"}</code>
     */
    private List<Map<String, Object>> variables;

    /**
     * 备注
     */
    private String remark;
}
