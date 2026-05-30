package com.nebula.common.oss.api;

import lombok.Data;

/**
 * 存储桶配置
 *
 * @author nebula
 */
@Data
public class BucketConfig {

    /**
     * 桶名称
     */
    private String bucketName;

    /**
     * 访问域名
     */
    private String domain;

    /**
     * 上传文件前缀路径
     */
    private String prefix;

    /**
     * 是否自动创建桶
     */
    private boolean autoCreate = true;

    public BucketConfig() {
    }

    public BucketConfig(String bucketName) {
        this.bucketName = bucketName;
    }

    public BucketConfig(String bucketName, String domain, String prefix) {
        this.bucketName = bucketName;
        this.domain = domain;
        this.prefix = prefix;
    }
}