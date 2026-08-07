package com.nebula.common.ai.harness.conversation;

import com.nebula.common.ai.flow.ToolDefinition;

/**
 * Harness 工具授权 SPI。
 *
 * <p>调用域只回答“工具能否用于生成控制面”，本接口进一步结合认证上下文回答“当前调用者能否使用”。
 *
 * @author nebula
 */
@FunctionalInterface
public interface HarnessToolAuthorizer {

    boolean isAuthorized(ToolDefinition tool, HarnessCallContext context);
}
