package com.nebula.manager.flow.dto;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * AI流程运行入参
 *
 * @author nebula
 */
@Data
public class FlowRunRequest {

    /**
     * 运行输入（注入编排上下文的初始变量）
     */
    private Map<String, Object> input = new HashMap<>();

    /**
     * 会话ID（启用记忆时用于隔离上下文）
     */
    private String conversationId;
}
