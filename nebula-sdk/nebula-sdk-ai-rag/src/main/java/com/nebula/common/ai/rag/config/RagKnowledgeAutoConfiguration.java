package com.nebula.common.ai.rag.config;

import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.knowledge.KnowledgeSearchToolDefinition;
import com.nebula.common.ai.rag.knowledge.KnowledgeService;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeBaseMapper;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeChunkMapper;
import com.nebula.common.ai.rag.knowledge.store.AiKnowledgeDocumentMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 知识库 RAG（场景①）自动装配
 * 仅当 {@code nebula.ai.rag.knowledge.enabled=true} 且具备 {@link EmbeddingProvider} + {@link VectorStore} + MyBatis 运行时
 * 才装配：注册知识库三表 Mapper、{@link KnowledgeService}、{@code knowledge_search} 工具。工具注册后经既有
 * {@code ToolRegistrySynchronizer} 自动镜像进 {@code ai_tool}，无需改编排内核。
 *
 * <p>装配顺序在 {@link MilvusAutoConfiguration} 之后，确保 {@link VectorStore} 先就绪。
 *
 * @author nebula
 */
@AutoConfiguration(after = MilvusAutoConfiguration.class)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnClass(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "nebula.ai.rag.knowledge", name = "enabled", havingValue = "true")
public class RagKnowledgeAutoConfiguration {

    /**
     * 默认切块窗口大小（字符）
     */
    private static final int DEFAULT_CHUNK_WINDOW = 800;

    /**
     * 默认切块重叠（字符）
     */
    private static final int DEFAULT_CHUNK_OVERLAP = 100;

    /**
     * 知识库服务。要求 {@link EmbeddingProvider} 与 {@link VectorStore} 均已装配。
     *
     * @param baseMapper        知识库 Mapper
     * @param documentMapper    文档 Mapper
     * @param chunkMapper       切片 Mapper
     * @param embeddingProvider embedding 提供者
     * @param vectorStore       向量存储
     * @param properties        AI 配置属性
     * @return 知识库服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({EmbeddingProvider.class, VectorStore.class})
    public KnowledgeService knowledgeService(AiKnowledgeBaseMapper baseMapper,
                                             AiKnowledgeDocumentMapper documentMapper,
                                             AiKnowledgeChunkMapper chunkMapper,
                                             EmbeddingProvider embeddingProvider,
                                             VectorStore vectorStore,
                                             AiProperties properties) {
        return new KnowledgeService(baseMapper, documentMapper, chunkMapper,
                embeddingProvider, vectorStore, properties.getEmbedding(),
                DEFAULT_CHUNK_WINDOW, DEFAULT_CHUNK_OVERLAP);
    }

    /**
     * 知识库检索工具（knowledge_search）。注册即被 ToolRegistrySynchronizer 镜像进 ai_tool。
     *
     * @param knowledgeService 知识库服务
     * @return 检索工具
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KnowledgeService.class)
    public KnowledgeSearchToolDefinition knowledgeSearchToolDefinition(KnowledgeService knowledgeService) {
        return new KnowledgeSearchToolDefinition(knowledgeService);
    }
}
