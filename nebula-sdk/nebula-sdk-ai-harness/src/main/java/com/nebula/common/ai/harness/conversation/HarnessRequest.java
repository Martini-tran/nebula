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
 * @param activeDraftId  当前会话正在编辑的草稿；非空时继续修改，禁止重复创建
 * @param activeDraftRevision 前端最后观测到的草稿 revision，仅用于提示模型，写入仍由 CAS 校验
 * @param confirmationToken 服务端确认授权；必须与 resumeAction 成对提交，仅注入恢复工具上下文
 * @param resumeAction      用户确认后恢复的结构化动作；不进入模型消息
 * @author nebula
 */
public record HarnessRequest(
        String prompt,
        List<HarnessMessage> messages,
        String conversationId,
        String model,
        Double temperature,
        String activeDraftId,
        Long activeDraftRevision,
        String confirmationToken,
        HarnessResumeAction resumeAction) {

    public HarnessRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public HarnessRequest(String prompt,
                          List<HarnessMessage> messages,
                          String conversationId,
                          String model,
                          Double temperature) {
        this(prompt, messages, conversationId, model, temperature, null, null, null, null);
    }

    public HarnessRequest(String prompt,
                          List<HarnessMessage> messages,
                          String conversationId,
                          String model,
                          Double temperature,
                          String confirmationToken,
                          HarnessResumeAction resumeAction) {
        this(prompt, messages, conversationId, model, temperature,
                null, null, confirmationToken, resumeAction);
    }

}
