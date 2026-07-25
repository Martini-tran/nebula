package com.nebula.common.ai.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.embedding.OpenAiEmbeddingProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * embedding 运行时自动装配
 * 仅当 {@code nebula.ai.embedding.enabled=true} 时装配 {@link OpenAiEmbeddingProvider}。复用 sdk-ai 的 {@code aiHttpClient}
 * 连接池（无则自建一个），端点/密钥/模型/维度走独立配置段 {@code nebula.ai.embedding.*}。
 *
 * <p>本类独立于 {@link MilvusAutoConfiguration}：embedding 可单独装配（如仅需向量化不需 Milvus 的测试场景），
 * 但四个检索场景需二者皆备方生效。
 *
 * @author nebula
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "nebula.ai.embedding", name = "enabled", havingValue = "true")
public class EmbeddingAutoConfiguration {

    /**
     * OpenAI 兼容 embedding 提供者。复用容器中的 {@code aiHttpClient}（来自 sdk-ai 的 AiAutoConfiguration）；
     * 若未装配 AI 运行时则自建一个默认连接池，保证 embedding 可独立启用。
     *
     * @param properties       AI 配置属性
     * @param httpClientProvider HTTP 客户端（缺省自建）
     * @param objectMapper     JSON 处理器（缺省自建）
     * @return embedding 提供者
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    public OpenAiEmbeddingProvider openAiEmbeddingProvider(AiProperties properties,
                                                           ObjectProvider<CloseableHttpClient> httpClientProvider,
                                                           ObjectProvider<ObjectMapper> objectMapper) {
        CloseableHttpClient httpClient = httpClientProvider.getIfAvailable(HttpClients::createDefault);
        return new OpenAiEmbeddingProvider(properties.getEmbedding(), httpClient,
                objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
