package com.nebula.common.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiCallLogger;
import com.nebula.common.ai.api.AiConversationRepository;
import com.nebula.common.ai.api.AiFilter;
import com.nebula.common.ai.api.AiProvider;
import com.nebula.common.ai.api.AiProviderResolver;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.provider.OpenAiChatProvider;
import com.nebula.common.ai.service.DefaultAiProviderResolver;
import com.nebula.common.ai.service.DefaultAiService;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * AI运行时自动装配
 * 仅当 {@code nebula.ai.enabled=true} 时生效，装配OpenAI兼容Provider、Provider解析器与
 * {@link AiService}。装配后 {@link AiService} Bean 出现，依赖它的各业务Agent（如博客Agent）方可工作。
 *
 * @author nebula
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "nebula.ai", name = "enabled", havingValue = "true")
public class AiAutoConfiguration {

    /**
     * AI调用专用HTTP客户端
     *
     * @return HTTP客户端
     */
    @Bean
    @ConditionalOnMissingBean(name = "aiHttpClient")
    public CloseableHttpClient aiHttpClient() {
        return HttpClients.createDefault();
    }

    /**
     * OpenAI兼容Chat服务提供商
     *
     * @param properties   AI配置属性
     * @param aiHttpClient HTTP客户端
     * @return AI服务提供商
     */
    @Bean
    @ConditionalOnMissingBean(OpenAiChatProvider.class)
    public OpenAiChatProvider openAiChatProvider(AiProperties properties, CloseableHttpClient aiHttpClient) {
        return new OpenAiChatProvider(properties.getOpenai(), aiHttpClient, new ObjectMapper());
    }

    /**
     * AI服务提供商解析器，聚合容器中全部 {@link AiProvider}
     *
     * @param providers  容器中全部AI服务提供商
     * @param properties AI配置属性
     * @return Provider解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public AiProviderResolver aiProviderResolver(ObjectProvider<AiProvider> providers, AiProperties properties) {
        return new DefaultAiProviderResolver(providers.orderedStream().toList(), properties.getProvider());
    }

    /**
     * AI服务统一入口
     *
     * @param resolver                AI服务提供商解析器
     * @param filters                 调用过滤器（可空）
     * @param conversationRepository  会话仓储（可空）
     * @param callLogger              调用日志记录器（可空）
     * @param eventPublisher          事件发布器
     * @param properties              AI配置属性
     * @return AI服务
     */
    @Bean
    @ConditionalOnMissingBean
    public AiService aiService(AiProviderResolver resolver,
                               ObjectProvider<AiFilter> filters,
                               ObjectProvider<AiConversationRepository> conversationRepository,
                               ObjectProvider<AiCallLogger> callLogger,
                               ApplicationEventPublisher eventPublisher,
                               AiProperties properties) {
        return new DefaultAiService(resolver,
                filters.orderedStream().toList(),
                conversationRepository.getIfAvailable(),
                callLogger.getIfAvailable(),
                eventPublisher,
                properties.getProvider());
    }
}
