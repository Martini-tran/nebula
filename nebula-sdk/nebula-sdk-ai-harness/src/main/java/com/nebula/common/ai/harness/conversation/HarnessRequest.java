package com.nebula.common.ai.harness.conversation;

import java.util.List;

/**
 * 流程生成 Harness 的 SDK 请求模型。
 *
 * <p>该模型刻意不依赖任何 Web/Manager DTO，宿主负责在传输边界完成转换。
 *
 * @param prompt         本轮用户输入
 * @param messages       历史消息
 * @param conversationId 会话标识
 * @param model          模型覆盖值
 * @param temperature    温度覆盖值
 * @author nebula
 */
public record HarnessRequest(
        String prompt,
        List<HarnessMessage> messages,
        String conversationId,
        String model,
        Double temperature) {

    public HarnessRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
