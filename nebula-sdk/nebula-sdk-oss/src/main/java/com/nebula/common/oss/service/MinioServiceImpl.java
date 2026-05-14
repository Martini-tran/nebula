package com.nebula.common.oss.service;

import com.nebula.common.oss.api.BucketConfig;
import com.nebula.common.oss.api.ObjectStorageService;
import com.nebula.common.oss.properties.MinioProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO对象存储服务实现
 *
 * @author nebula
 */
@Slf4j
@RequiredArgsConstructor
public class MinioServiceImpl implements ObjectStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String STORAGE_TYPE = "minio";

    @Override
    public String upload(String bucketName, InputStream inputStream, String objectKey, String contentType, long size) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        String actualBucketName = bucketConfig.getBucketName();

        // 自动创建桶
        if (bucketConfig.isAutoCreate() && !bucketExists(actualBucketName)) {
            createBucket(actualBucketName);
        }

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(actualBucketName)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(inputStream, size, -1)
                    .build());

            log.debug("MinIO upload success, bucket: {}, objectKey: {}", actualBucketName, objectKey);
            return getUrl(actualBucketName, objectKey);
        } catch (Exception e) {
            log.error("MinIO upload failed, bucket: {}, objectKey: {}", actualBucketName, objectKey, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public String upload(String bucketName, InputStream inputStream, String originalFilename, String contentType) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        String objectKey = generateObjectKey(originalFilename, bucketConfig.getPrefix());
        return upload(bucketConfig.getBucketName(), inputStream, objectKey, contentType, -1);
    }

    @Override
    public String upload(String bucketName, byte[] bytes, String originalFilename, String contentType) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        String objectKey = generateObjectKey(originalFilename, bucketConfig.getPrefix());
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            return upload(bucketConfig.getBucketName(), inputStream, objectKey, contentType, bytes.length);
        } catch (Exception e) {
            log.error("MinIO upload byte array failed", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketConfig.getBucketName())
                    .object(objectKey)
                    .build());
            log.debug("MinIO delete success, bucket: {}, objectKey: {}", bucketConfig.getBucketName(), objectKey);
        } catch (Exception e) {
            log.error("MinIO delete failed, bucket: {}, objectKey: {}", bucketConfig.getBucketName(), objectKey, e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

    @Override
    public boolean exists(String bucketName, String objectKey) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketConfig.getBucketName())
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            log.error("MinIO check exists failed, bucket: {}, objectKey: {}", bucketConfig.getBucketName(), objectKey, e);
            return false;
        } catch (Exception e) {
            log.error("MinIO check exists failed, bucket: {}, objectKey: {}", bucketConfig.getBucketName(), objectKey, e);
            return false;
        }
    }

    @Override
    public InputStream getInputStream(String bucketName, String objectKey) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketConfig.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("MinIO get inputStream failed, bucket: {}, objectKey: {}", bucketConfig.getBucketName(), objectKey, e);
            throw new RuntimeException("获取文件失败", e);
        }
    }

    @Override
    public String getUrl(String bucketName, String objectKey) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        if (bucketConfig.getDomain() != null && !bucketConfig.getDomain().isEmpty()) {
            return bucketConfig.getDomain() + "/" + bucketConfig.getBucketName() + "/" + objectKey;
        }
        String protocol = minioProperties.isUseSSL() ? "https" : "http";
        return protocol + "://" + minioProperties.getEndpoint() + "/" + bucketConfig.getBucketName() + "/" + objectKey;
    }

    @Override
    public String getPresignedUrl(String bucketName, String objectKey, int expirySeconds) {
        BucketConfig bucketConfig = minioProperties.getBucketConfig(bucketName);
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketConfig.getBucketName())
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            log.error("MinIO get presigned url failed, bucket: {}, objectKey: {}", bucketConfig.getBucketName(), objectKey, e);
            throw new RuntimeException("获取预览URL失败", e);
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO check bucket exists failed, bucket: {}", bucketName, e);
            return false;
        }
    }

    @Override
    public void createBucket(String bucketName) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
            log.info("MinIO bucket created: {}", bucketName);
        } catch (Exception e) {
            log.error("MinIO create bucket failed, bucket: {}", bucketName, e);
            throw new RuntimeException("创建桶失败", e);
        }
    }

    @Override
    public String generateObjectKey(String originalFilename, String prefix) {
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String extension = getFileExtension(originalFilename);

        StringBuilder objectKey = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            objectKey.append(prefix).append("/");
        }
        objectKey.append(datePath).append("/").append(uuid);
        if (extension != null && !extension.isEmpty()) {
            objectKey.append(".").append(extension);
        }

        return objectKey.toString();
    }

    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return null;
        }
        return filename.substring(lastDot + 1);
    }
}