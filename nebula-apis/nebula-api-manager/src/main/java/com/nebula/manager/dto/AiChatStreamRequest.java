package com.nebula.manager.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 聊天流式请求
 * 承载一次流式对话所需的提示词与历史消息；模型相关参数为空时回退全局配置。
 *
 * @author nebula
 */
@Data
public class AiChatStreamRequest {

    /**
     * 本轮用户提示词
     */
    private String prompt;

    /**
     * 历史消息（可空）。
     * 元素形如 {@code {role:"user"|"assistant"|"system", content:"..."}}，
     * 非空时与 prompt 一起构成下发给模型的完整上下文。
     */
    private List<Message> messages = new ArrayList<>();

    /**
     * 关联会话ID（可空）
     */
    private String conversationId;

    /**
     * 模型名称（可空，回退全局配置）
     */
    private String model;

    /**
     * 温度参数（可空，回退全局配置）
     */
    private Double temperature;

    /**
     * 单条对话消息
     */
    @Data
    public static class Message {

        /**
         * 角色：user / assistant / system
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;
    }
}
