package com.nebula.common.ai.harness.conversation;

import java.util.Set;

/**
 * 一次 Harness 调用的已认证上下文。
 *
 * <p>身份、会话和权限由服务端认证边界构造，不作为模型工具参数。记录使用不可变集合，确保跨 SSE 与工具
 * 工作线程传递时不会发生身份漂移。
 *
 * @param userId      已认证用户ID
 * @param sessionId   归属会话ID
 * @param permissions 权限快照
 * @param requestId   请求链路ID
 * @author nebula
 */
public record HarnessCallContext(
        String userId,
        String sessionId,
        Set<String> permissions,
        String requestId) {

    public HarnessCallContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * @return 是否具有可追踪的认证身份
     */
    public boolean authenticated() {
        return userId != null && !userId.isBlank();
    }
}
