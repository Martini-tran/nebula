package com.nebula.manager.dto;

import lombok.Data;

/** Copilot 高风险动作确认请求；会话字段只用于草稿的条件访问边界。 */
@Data
public class CopilotConfirmationRequest {

    private String conversationId;
}
