package com.nebula.manager.ai.copilot;

import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.conversation.HarnessPromptProvider;
import com.nebula.common.ai.harness.conversation.HarnessRequest;
import org.springframework.stereotype.Component;

/**
 * Manager 对 Flow Harness 的系统提示词适配器。
 *
 * <p>提示词所有权保留在业务模块，SDK 仅通过 SPI 获取，避免反向依赖 manager。
 *
 * @author nebula
 */
@Component
public class CopilotHarnessPromptProvider implements HarnessPromptProvider {

    @Override
    public String systemPrompt(HarnessRequest request, HarnessCallContext context) {
        return CopilotSystemPrompt.TEXT;
    }
}
