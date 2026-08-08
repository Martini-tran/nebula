package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.flow.ToolContext;

/**
 * 服务端草稿访问上下文。
 *
 * <p>身份只能从已认证的 {@link ToolContext} 构造，禁止将 userId/sessionId 暴露为模型工具参数。
 *
 * @param userId    已认证用户 ID
 * @param sessionId 当前会话 ID，可空
 * @author nebula
 */
public record DraftAccess(Long userId, String sessionId) {

    public static DraftAccess from(ToolContext context) {
        if (context == null || context.userId() == null || context.userId().isBlank()) {
            throw new IllegalArgumentException("缺少已认证的用户身份");
        }
        try {
            return new DraftAccess(Long.valueOf(context.userId()), blankToNull(context.conversationId()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("用户身份格式无效");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
