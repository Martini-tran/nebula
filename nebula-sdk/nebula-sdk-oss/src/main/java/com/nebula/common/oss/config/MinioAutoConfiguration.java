package com.nebula.common.oss.config;

import com.nebula.common.oss.api.ObjectStorageFactory;
import com.nebula.common.oss.api.ObjectStorageService;
import com.nebula.common.oss.api.StorageType;
import com.nebula.common.oss.properties.MinioProperties;
import com.nebula.common.oss.service.MinioServiceImpl;
import com.nebula.common.oss.service.SingleStorageFactory;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO自动配置
 *
 * @author nebula
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@RequiredArgsConstructor
public class MinioAutoConfiguration {

    private final MinioProperties minioProperties;

    @Bean
    @ConditionalOnProperty(prefix = "nebula.minio", name = "enabled", havingValue = "true")
    public MinioClient minioClient() {
        String protocol = minioProperties.isUseSSL() ? "https://" : "http://";
        String fullEndpoint = protocol + minioProperties.getEndpoint();
        return MinioClient.builder()
                .endpoint(fullEndpoint)
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "nebula.minio", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ObjectStorageService.class)
    public ObjectStorageService minioObjectStorageService(MinioClient minioClient) {
        return new MinioServiceImpl(minioClient, minioProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "nebula.minio", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(ObjectStorageFactory.class)
    public ObjectStorageFactory minioStorageFactory(ObjectStorageService minioObjectStorageService) {
        return new SingleStorageFactory(minioObjectStorageService, StorageType.MINIO);
    }
}