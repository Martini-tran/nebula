package com.nebula.common.ai.rag.config;

import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.flowexample.FlowExampleService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Flow Copilot few-shot（场景②）自动装配。
 *
 * <p>仅当 {@code nebula.ai.rag.fewShot.enabled=true} 且具备 {@link EmbeddingProvider} + {@link VectorStore} 才装配
 * {@link FlowExampleService}（纯向量 CRUD 能力）。索引器 {@code FlowExampleIndexer} 与召回接线在 manager 侧
 * （读 {@code ai_flow} + Copilot「生成前」时机），本配置只提供 SDK 能力。
 *
 * <p>{@code enabled=false}（默认）时本 Bean 缺省，manager 侧经 {@code ObjectProvider} 优雅降级——Copilot 退化为
 * 零样本生成，行为零变化。装配顺序在 {@link MilvusAutoConfiguration} 之后，确保 {@link VectorStore} 先就绪。
 *
 * @author nebula
 */
@AutoConfiguration(after = MilvusAutoConfiguration.class)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "nebula.ai.rag.fewShot", name = "enabled", havingValue = "true")
public class RagFewShotAutoConfiguration {

    /**
     * Flow few-shot 示例服务。要求 {@link EmbeddingProvider} 与 {@link VectorStore} 均已装配。
     *
     * @param vectorStore       向量存储
     * @param embeddingProvider embedding 提供者
     * @return few-shot 示例服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({EmbeddingProvider.class, VectorStore.class})
    public FlowExampleService flowExampleService(VectorStore vectorStore, EmbeddingProvider embeddingProvider) {
        return new FlowExampleService(vectorStore, embeddingProvider);
    }
}
