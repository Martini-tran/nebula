package com.nebula.common.file.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件信息 VO
 *
 * @author nebula
 */
@Data
public class FileInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    private Long id;

    /**
     * 关联业务类型
     */
    private String targetType;

    /**
     * 关联业务实体ID
     */
    private Long targetId;

    /**
     * 文件用途类型
     */
    private String fileType;

    /**
     * 存储类型
     */
    private String storageType;

    /**
     * 存储平台标识
     */
    private String storagePlatform;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 对象存储Key
     */
    private String objectKey;

    /**
     * 访问URL（公开桶为永久URL，私有桶为带签名的临时URL）
     */
    private String url;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 存储文件名
     */
    private String storedFilename;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private Long sizeBytes;

    /**
     * 图片宽度
     */
    private Integer width;

    /**
     * 图片高度
     */
    private Integer height;

    /**
     * 音视频时长（秒）
     */
    private Integer duration;

    /**
     * SHA256
     */
    private String hashSha256;

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 文件状态
     */
    private Integer status;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 上传人ID
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
