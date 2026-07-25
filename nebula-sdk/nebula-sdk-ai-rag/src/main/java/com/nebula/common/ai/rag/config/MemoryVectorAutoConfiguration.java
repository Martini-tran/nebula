package com.nebula.common.ai.rag.config;

import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.memory.VectorLongTermMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 长期记忆语义召回（场景③）自动装配。
 *
 * <p>仅当 {@code nebula.ai.rag.memory.mode=vector} 且已具备 {@link EmbeddingProvider} + {@link VectorStore} + 底层
 * {@link LongTermMemory}（数据库版真相源）时，注册 {@link VectorLongTermMemory} 作为 {@link Primary} 长期记忆——
 * 包住数据库版实现，写入双写 DB + Milvus，检索走向量语义召回。
 *
 * <p>{@code mode=db}（默认）时本配置不生效，{@code AiMemoryAutoConfiguration} 仍取到数据库版实现，行为零变化。
 * 装配在 {@link MilvusAutoConfiguration} 之后，确保 {@link VectorStore} 先就绪。
 *
 * @author nebula
 */
@AutoConfiguration(after = MilvusAutoConfiguration.class)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "nebula.ai.rag.memory", name = "mode", havingValue = "vector")
public class MemoryVectorAutoConfiguration {

    /**
     * 向量语义召回长期记忆。装饰底层数据库版 {@link LongTermMemory}，标记 {@link Primary} 使记忆门面优先取本实现。
     *
     * <p>{@code delegate} 经 {@link ObjectProvider} 显式取非 {@link VectorLongTermMemory} 的底层实现，避免自引用，
     * 并在未来出现多个 {@link LongTermMemory} 实现时仍能明确锁定被装饰者（不依赖"恰好只有一个候选"的隐性前提）。
     *
     * @param longTermMemories  容器中全部长期记忆实现（用于筛出底层被装饰者）
     * @param embeddingProvider embedding 提供者
     * @param vectorStore       向量存储
     * @param properties        AI 配置属性
     * @return 向量语义召回长期记忆
     */
    @Bean
    @Primary
    @ConditionalOnBean({EmbeddingProvider.class, VectorStore.class, LongTermMemory.class})
    public VectorLongTermMemory vectorLongTermMemory(ObjectProvider<LongTermMemory> longTermMemories,
                                                     EmbeddingProvider embeddingProvider,
                                                     VectorStore vectorStore,
                                                     AiProperties properties) {
        LongTermMemory delegate = longTermMemories.stream()
                .filter(m -> !(m instanceof VectorLongTermMemory))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "nebula.ai.rag.memory.mode=vector 需要底层数据库版 LongTermMemory 作为真相源，但未找到"));
        return new VectorLongTermMemory(delegate, vectorStore, embeddingProvider,
                properties.getRag().getMemory());
    }
}
