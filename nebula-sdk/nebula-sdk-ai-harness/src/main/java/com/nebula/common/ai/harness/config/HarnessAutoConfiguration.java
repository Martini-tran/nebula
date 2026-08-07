package com.nebula.common.ai.harness.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.config.FlowAutoConfiguration;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.conversation.HarnessExampleProvider;
import com.nebula.common.ai.harness.conversation.HarnessPromptProvider;
import com.nebula.common.ai.harness.conversation.HarnessToolAuthorizer;
import com.nebula.common.ai.harness.runtime.FlowGenerationHarness;
import com.nebula.common.ai.harness.runtime.HarnessToolScheduler;
import com.nebula.common.ai.properties.AiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流程生成 Harness 自动装配。
 *
 * @author nebula
 */
@AutoConfiguration(after = FlowAutoConfiguration.class)
@ConditionalOnBean({AiService.class, ToolRegistry.class})
public class HarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HarnessToolAuthorizer harnessToolAuthorizer() {
        return (tool, context) -> context != null && context.authenticated();
    }

    @Bean(name = "harnessToolExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "harnessToolExecutor")
    public ExecutorService harnessToolExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "ai-harness-tool-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newCachedThreadPool(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public HarnessToolScheduler harnessToolScheduler(ToolRegistry toolRegistry,
                                                     AiService aiService,
                                                     ObjectProvider<ObjectMapper> objectMapper,
                                                     HarnessToolAuthorizer authorizer,
                                                     @Qualifier("harnessToolExecutor") ExecutorService harnessToolExecutor,
                                                     AiProperties aiProperties) {
        int timeoutMs = aiProperties == null ? 10000 : aiProperties.getToolCalling().getToolTimeoutMs();
        return new HarnessToolScheduler(toolRegistry, aiService,
                objectMapper.getIfAvailable(ObjectMapper::new), authorizer, harnessToolExecutor, timeoutMs);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGenerationHarness flowGenerationHarness(
            AiService aiService,
            HarnessToolScheduler toolScheduler,
            ObjectProvider<HarnessPromptProvider> promptProviders,
            ObjectProvider<HarnessExampleProvider> exampleProviders,
            AiProperties aiProperties) {
        int maxIterations = aiProperties == null ? 5 : aiProperties.getToolCalling().getMaxIterations();
        return new FlowGenerationHarness(aiService, toolScheduler,
                promptProviders.orderedStream().toList(),
                exampleProviders.orderedStream().toList(),
                maxIterations);
    }
}
