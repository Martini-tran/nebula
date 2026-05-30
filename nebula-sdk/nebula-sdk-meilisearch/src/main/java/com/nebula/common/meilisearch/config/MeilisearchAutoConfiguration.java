package com.nebula.common.meilisearch.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.nebula.common.meilisearch.api.IndexConfig;
import com.nebula.common.meilisearch.api.MeilisearchService;
import com.nebula.common.meilisearch.properties.MeilisearchProperties;
import com.nebula.common.meilisearch.service.MeilisearchServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Meilisearch 自动配置
 *
 * @author nebula
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MeilisearchProperties.class)
@ConditionalOnProperty(prefix = "nebula.meilisearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MeilisearchAutoConfiguration {

    private final MeilisearchProperties properties;

    @Bean
    @ConditionalOnMissingBean(Client.class)
    public Client meilisearchClient() {
        Config config = new Config(properties.getHost(), properties.getApiKey() == null ? "" : properties.getApiKey());
        return new Client(config);
    }

    @Bean
    @ConditionalOnMissingBean(MeilisearchService.class)
    public MeilisearchService meilisearchService(Client meilisearchClient) {
        return new MeilisearchServiceImpl(meilisearchClient, properties);
    }

    @Bean
    public ApplicationRunner meilisearchIndexInitializer(MeilisearchService meilisearchService) {
        return args -> {
            Map<String, IndexConfig> indexes = properties.getIndexes();
            if (indexes == null || indexes.isEmpty()) {
                return;
            }
            indexes.forEach((key, indexConfig) -> {
                if (indexConfig.getUid() == null || indexConfig.getUid().isEmpty()) {
                    indexConfig.setUid(key);
                }
                try {
                    meilisearchService.createIndexIfAbsent(indexConfig);
                } catch (Exception e) {
                    log.warn("初始化 Meilisearch 索引失败: {} - {}", indexConfig.getUid(), e.getMessage());
                }
            });
        };
    }
}
