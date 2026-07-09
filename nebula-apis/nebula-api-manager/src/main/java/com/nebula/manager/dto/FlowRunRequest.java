package com.nebula.manager.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI流程运行请求
 * 一键运行流程时传入的初始输入与会话标识。
 *
 * @author nebula
 */
@Data
public class FlowRunRequest {

    /**
     * 初始输入（写入编排上下文，供首个节点的模板引用）
     */
    private Map<String, Object> input = new LinkedHashMap<>();

    /**
     * 关联会话ID（可空）
     */
    private String conversationId;
}
