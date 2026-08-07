package com.nebula.common.ai.harness.conversation;

/**
 * Harness 系统提示词 SPI。
 *
 * <p>SDK 只负责调用，不持有宿主业务提示词。
 *
 * @author nebula
 */
@FunctionalInterface
public interface HarnessPromptProvider {

    /**
     * @param request 请求
     * @param context 已认证上下文
     * @return 系统提示词；空值表示不追加
     */
    String systemPrompt(HarnessRequest request, HarnessCallContext context);
}
