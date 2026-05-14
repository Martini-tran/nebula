package com.nebula.common.oss.api;

import lombok.Getter;

/**
 * 存储类型枚举
 *
 * @author nebula
 */
@Getter
public enum StorageType {

    /**
     * MinIO
     */
    MINIO("minio"),

    /**
     * 阿里云OSS
     */
    ALIYUN_OSS("aliyun-oss"),

    /**
     * 腾讯云COS
     */
    TENCENT_COS("tencent-cos"),

    /**
     * AWS S3
     */
    AWS_S3("aws-s3"),

    /**
     * 本地存储
     */
    LOCAL("local");

    private final String code;

    StorageType(String code) {
        this.code = code;
    }

    public static StorageType fromCode(String code) {
        for (StorageType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}