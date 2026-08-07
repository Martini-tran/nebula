package com.nebula.common.ai.harness.conversation;

import java.util.List;

/**
 * Harness few-shot / 范式召回 SPI。
 *
 * <p>返回已预算化的 system 消息片段；召回失败应由实现降级为空集合，不阻断生成主链路。
 *
 * @author nebula
 */
@FunctionalInterface
public interface HarnessExampleProvider {

    /**
     * @param request 请求
     * @param context 已认证上下文
     * @return 示例提示词片段
     */
    List<String> examplePrompts(HarnessRequest request, HarnessCallContext context);
}
