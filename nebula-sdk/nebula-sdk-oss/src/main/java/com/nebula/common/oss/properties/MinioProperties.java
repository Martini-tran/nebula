package com.nebula.common.oss.properties;

import com.nebula.common.oss.api.BucketConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * MinIO配置属性
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.minio")
public class MinioProperties {

    /**
     * 是否启用MinIO
     */
    private boolean enabled = false;

    /**
     * endpoint
     */
    private String endpoint;

    /**
     * accessKey
     */
    private String accessKey;

    /**
     * secretKey
     */
    private String secretKey;

    /**
     * 是否使用SSL
     */
    private boolean useSSL = false;

    /**
     * 默认桶名称
     */
    private String defaultBucket;

    /**
     * 桶配置映射（支持多桶）
     */
    private Map<String, BucketConfig> buckets = new HashMap<>();

    /**
     * 获取桶配置
     *
     * @param bucketName 桶名称
     * @return 桶配置
     */
    public BucketConfig getBucketConfig(String bucketName) {
        BucketConfig config = buckets.get(bucketName);
        if (config == null) {
            config = new BucketConfig(bucketName);
        }
        if (config.getBucketName() == null) {
            config.setBucketName(bucketName);
        }
        return config;
    }
}