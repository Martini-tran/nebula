package com.nebula.manager.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 用户确认后恢复的结构化 Copilot 动作；该字段不会进入模型上下文。 */
@Data
public class CopilotResumeAction {

    private String toolCode;

    private Map<String, Object> arguments = new LinkedHashMap<>();
}
