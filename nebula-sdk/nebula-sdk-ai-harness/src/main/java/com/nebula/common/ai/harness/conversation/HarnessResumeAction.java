package com.nebula.common.ai.harness.conversation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户确认后由传输层恢复的结构化 Harness 动作。
 *
 * <p>恢复动作不进入模型消息，只能由 Harness 显式白名单执行；参数仍需经过工具授权和 JSON Schema 校验。
 *
 * @param toolCode  工具编码
 * @param arguments 首次调用时的原始结构化参数
 * @author nebula
 */
public record HarnessResumeAction(String toolCode, Map<String, Object> arguments) {

    public HarnessResumeAction {
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
    }
}
