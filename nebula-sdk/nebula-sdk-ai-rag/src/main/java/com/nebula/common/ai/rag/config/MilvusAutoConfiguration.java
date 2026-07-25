package com.nebula.common.ai.rag.config;

import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.milvus.CollectionInitializer;
import com.nebula.common.ai.rag.milvus.CollectionResolver;
import com.nebula.common.ai.rag.milvus.MilvusVectorStore;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Milvus 运行时自动装配
 * 仅当 {@code nebula.ai.milvus.enabled=true} 时装配 {@link MilvusClientV2} 与 {@link MilvusVectorStore}。连接支持
 * host+port（standalone/cluster）与 uri+token（Zilliz Cloud）两态。{@link CollectionInitializer} 依赖 {@link EmbeddingProvider}
 * 取维度建库，故仅在 embedding 已装配（{@code @ConditionalOnBean(EmbeddingProvider)}）时才注册初始化器。
 *
 * <p>装配顺序在 {@link EmbeddingAutoConfiguration} 之后，确保 {@link EmbeddingProvider} 先就绪。
 *
 * @author nebula
 */
@AutoConfiguration(after = EmbeddingAutoConfiguration.class)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "nebula.ai.milvus", name = "enabled", havingValue = "true")
public class MilvusAutoConfiguration {

    /**
     * Milvus v2 客户端。uri 非空走 Zilliz Cloud 整串；否则用 host+port。
     *
     * @param properties AI 配置属性
     * @return Milvus 客户端
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MilvusClientV2 milvusClientV2(AiProperties properties) {
        AiProperties.Milvus milvus = properties.getMilvus();
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .connectTimeoutMs(milvus.getConnectTimeoutMs());
        if (StringUtils.hasText(milvus.getUri())) {
            builder.uri(milvus.getUri());
        } else {
            String scheme = milvus.isSecure() ? "https://" : "http://";
            builder.uri(scheme + milvus.getHost() + ":" + milvus.getPort());
        }
        if (StringUtils.hasText(milvus.getToken())) {
            builder.token(milvus.getToken());
        }
        if (StringUtils.hasText(milvus.getDatabase())) {
            builder.dbName(milvus.getDatabase());
        }
        builder.secure(milvus.isSecure());
        return new MilvusClientV2(builder.build());
    }

    /**
     * collection 逻辑名解析器。维度取自 {@link EmbeddingProvider}，保证物理名与建库维度一致。
     *
     * @param properties        AI 配置属性
     * @param embeddingProvider embedding 提供者（取维度）
     * @return collection 解析器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EmbeddingProvider.class)
    public CollectionResolver collectionResolver(AiProperties properties, EmbeddingProvider embeddingProvider) {
        return new CollectionResolver(properties.getMilvus(), embeddingProvider.dimension());
    }

    /**
     * Milvus 版向量存储。
     *
     * @param client     Milvus 客户端
     * @param properties AI 配置属性
     * @param resolver   collection 解析器
     * @return 向量存储
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnBean({MilvusClientV2.class, CollectionResolver.class})
    public MilvusVectorStore milvusVectorStore(MilvusClientV2 client,
                                               AiProperties properties,
                                               CollectionResolver resolver) {
        return new MilvusVectorStore(client, properties.getMilvus(), resolver);
    }

    /**
     * collection 初始化器（幂等建库/建索引/建 alias）。依赖 {@link EmbeddingProvider} 取维度，无则不装配。
     *
     * @param client              Milvus 客户端
     * @param properties          AI 配置属性
     * @param resolver            collection 解析器
     * @param embeddingProvider   embedding 提供者
     * @return 初始化器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({MilvusClientV2.class, CollectionResolver.class, EmbeddingProvider.class})
    public CollectionInitializer collectionInitializer(MilvusClientV2 client,
                                                       AiProperties properties,
                                                       CollectionResolver resolver,
                                                       EmbeddingProvider embeddingProvider) {
        return new CollectionInitializer(client, properties.getMilvus(), resolver, embeddingProvider);
    }
}
