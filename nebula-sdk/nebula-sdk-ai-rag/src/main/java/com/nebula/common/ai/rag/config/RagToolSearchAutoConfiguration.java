package com.nebula.common.ai.rag.config;

import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.toolcatalog.SearchToolsToolDefinition;
import com.nebula.common.ai.rag.toolcatalog.ToolCatalogIndexer;
import com.nebula.common.ai.rag.toolcatalog.ToolCatalogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 工具语义检索（场景④）自动装配。
 *
 * <p>仅当 {@code nebula.ai.rag.toolSearch.enabled=true} 且具备 {@link EmbeddingProvider} + {@link VectorStore} 才装配：
 * 注册 {@link ToolCatalogService}（纯向量 CRUD）、{@code search_tools} 工具（注册后经既有 {@code ToolRegistrySynchronizer}
 * 自动镜像进 {@code ai_tool}，Copilot / Agent 可调用）、{@link ToolCatalogIndexer}（启动遍历 {@code ToolRegistry.all()}
 * 索引工具全集）。
 *
 * <p>装配顺序在 {@link MilvusAutoConfiguration} 之后，确保 {@link VectorStore} 先就绪。
 *
 * @author nebula
 */
@AutoConfiguration(after = MilvusAutoConfiguration.class)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "nebula.ai.rag.toolSearch", name = "enabled", havingValue = "true")
public class RagToolSearchAutoConfiguration {

    /**
     * 工具目录检索服务。要求 {@link EmbeddingProvider} 与 {@link VectorStore} 均已装配。
     *
     * @param vectorStore       向量存储
     * @param embeddingProvider embedding 提供者
     * @return 工具目录服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({EmbeddingProvider.class, VectorStore.class})
    public ToolCatalogService toolCatalogService(VectorStore vectorStore, EmbeddingProvider embeddingProvider) {
        return new ToolCatalogService(vectorStore, embeddingProvider);
    }

    /**
     * 工具语义检索工具（search_tools）。注册即被 ToolRegistrySynchronizer 镜像进 ai_tool。
     *
     * <p>{@link ToolRegistry} 经 {@link ObjectProvider} 惰性注入，避免「search_tools → ToolRegistry → search_tools」
     * 循环依赖（本工具自身也是被 ToolRegistry 聚合的 ToolDefinition）。
     *
     * @param toolCatalogService   工具目录服务
     * @param toolRegistryProvider 工具注册表（惰性）
     * @return 工具语义检索工具
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ToolCatalogService.class)
    public SearchToolsToolDefinition searchToolsToolDefinition(ToolCatalogService toolCatalogService,
                                                               ObjectProvider<ToolRegistry> toolRegistryProvider) {
        return new SearchToolsToolDefinition(toolCatalogService, toolRegistryProvider);
    }

    /**
     * 工具目录索引器：启动遍历 {@code ToolRegistry.all()} 把工具全集向量化。
     *
     * @param toolCatalogService 工具目录服务
     * @param toolRegistry       工具注册表
     * @return 索引器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ToolCatalogService.class, ToolRegistry.class})
    public ToolCatalogIndexer toolCatalogIndexer(ToolCatalogService toolCatalogService, ToolRegistry toolRegistry) {
        return new ToolCatalogIndexer(toolCatalogService, toolRegistry);
    }
}
