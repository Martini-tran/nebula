package com.nebula.common.file.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * 文件管理配置属性
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.file")
public class FileProperties {

    /**
     * 是否启用文件管理（默认启用）
     */
    private boolean enabled = true;

    /**
     * 默认存储桶名称（不传 bucket 时使用）
     */
    private String defaultBucket;

    /**
     * 单文件最大大小（字节），默认 50MB
     */
    private long maxFileSize = 50L * 1024 * 1024;

    /**
     * 临时URL默认有效期（秒），默认 1 小时
     */
    private int presignedUrlExpirySeconds = 3600;

    /**
     * 临时URL最大有效期（秒），默认 7 天
     */
    private int maxPresignedUrlExpirySeconds = 7 * 24 * 3600;

    /**
     * 允许的图片 MIME 类型
     */
    private Set<String> allowedImageMimeTypes = new HashSet<>(Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/svg+xml", "image/bmp", "image/tiff"
    ));

    /**
     * 允许的视频 MIME 类型
     */
    private Set<String> allowedVideoMimeTypes = new HashSet<>(Set.of(
            "video/mp4", "video/webm", "video/ogg", "video/quicktime"
    ));

    /**
     * 是否限制 MIME 类型白名单（默认 false，不限制）
     * 开启后只允许 image/video/通用附件白名单内的文件
     */
    private boolean strictMimeCheck = false;
}
