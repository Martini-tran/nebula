package com.nebula.common.ai.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI调用请求
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class AiRequest {

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 对话ID
     */
    private String conversationId;

    /**
     * Agent功能编码
     */
    private String agentCode;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 服务提供商
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 提示词
     */
    private String prompt;

    /**
     * 消息列表
     */
    private List<Map<String, Object>> messages = new ArrayList<>();

    /**
     * 是否流式响应
     */
    private boolean stream = false;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大输出token数
     */
    private Integer maxTokens;

    /**
     * Top P采样参数
     */
    private Double topP;

    /**
     * 停止词
     */
    private List<String> stop = new ArrayList<>();

    /**
     * 模板参数
     */
    private Map<String, Object> variables = new HashMap<>();

    /**
     * 扩展参数
     */
    private Map<String, Object> options = new HashMap<>();

    public AiRequest() {
    }

    public AiRequest(String prompt) {
        this.prompt = prompt;
    }

    public AiRequest(List<Map<String, Object>> messages) {
        this.messages = messages;
    }

    public String getPrompt() {
        return prompt;
    }

    public AiRequest setPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public AiRequest setMessages(List<Map<String, Object>> messages) {
        this.messages = messages;
        return this;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public AiRequest setVariables(Map<String, Object> variables) {
        this.variables = variables;
        return this;
    }
}
