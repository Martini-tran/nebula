package com.nebula.common.ai.harness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 流程生成对话循环配置。 */
@ConfigurationProperties(prefix = "nebula.ai.harness.conversation")
public class HarnessConversationProperties {

    /**
     * 一次完整建图通常包含十余次细粒度 mutation，不能复用通用工具调用的 5 轮缺省值。
     */
    private int maxIterations = 30;

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = Math.max(1, Math.min(maxIterations, 100));
    }
}
