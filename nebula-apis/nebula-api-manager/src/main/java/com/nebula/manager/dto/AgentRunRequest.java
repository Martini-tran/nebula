package com.nebula.manager.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 智能体运行请求
 * 按 agentCode 创建实例并执行时传入的入参与会话标识。
 *
 * @author nebula
 */
@Data
public class AgentRunRequest {

    /**
     * 本次调用入参（写入编排上下文，Inputs 覆盖 Import 的同名键）
     */
    private Map<String, Object> inputs = new LinkedHashMap<>();

    /**
     * 关联会话ID（可空）
     */
    private String conversationId;
}
