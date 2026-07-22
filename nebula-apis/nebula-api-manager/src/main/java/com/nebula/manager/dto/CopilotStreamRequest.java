package com.nebula.manager.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程设计助手（Copilot）流式请求
 * 承载一次「对话式生成流程/派生 Agent」所需的用户输入与历史消息。会话无状态：多轮上下文由前端在
 * {@code messages} 中携带；工具循环内追加的 assistant/tool 中间消息仅活在本轮、循环结束即丢弃。
 * 模型相关参数为空时回退全局配置。
 *
 * @author nebula
 */
@Data
public class CopilotStreamRequest {

    /**
     * 本轮用户输入
     */
    private String prompt;

    /**
     * 历史消息（可空）。
     * 元素形如 {@code {role:"user"|"assistant"|"system", content:"..."}}，
     * 非空时与 prompt 一起构成下发给模型的完整上下文（不含 system 提示词，system 由后端注入）。
     */
    private List<Message> messages = new ArrayList<>();

    /**
     * 关联会话ID（可空，仅用于审计/日志维度，不驱动服务端落库）
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
