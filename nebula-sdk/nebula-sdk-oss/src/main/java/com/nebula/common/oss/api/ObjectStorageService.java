package com.nebula.common.oss.api;

import java.io.InputStream;

/**
 * 对象存储服务接口
 *
 * @author nebula
 */
public interface ObjectStorageService {

    /**
     * 上传文件
     *
     * @param bucketName 桶名称
     * @param inputStream 文件流
     * @param objectKey 对象key
     * @param contentType 文件类型
     * @param size 文件大小，-1表示未知
     * @return 文件访问URL
     */
    String upload(String bucketName, InputStream inputStream, String objectKey, String contentType, long size);

    /**
     * 上传文件（自动生成objectKey）
     *
     * @param bucketName 桶名称
     * @param inputStream 文件流
     * @param originalFilename 原始文件名
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    String upload(String bucketName, InputStream inputStream, String originalFilename, String contentType);

    /**
     * 上传字节数组
     *
     * @param bucketName 桶名称
     * @param bytes 字节数组
     * @param originalFilename 原始文件名
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    String upload(String bucketName, byte[] bytes, String originalFilename, String contentType);

    /**
     * 删除文件
     *
     * @param bucketName 桶名称
     * @param objectKey 对象key
     */
    void delete(String bucketName, String objectKey);

    /**
     * 检查文件是否存在
     *
     * @param bucketName 桶名称
     * @param objectKey 对象key
     * @return 是否存在
     */
    boolean exists(String bucketName, String objectKey);

    /**
     * 获取文件流
     *
     * @param bucketName 桶名称
     * @param objectKey 对象key
     * @return 文件流
     */
    InputStream getInputStream(String bucketName, String objectKey);

    /**
     * 获取文件访问URL
     *
     * @param bucketName 桶名称
     * @param objectKey 对象key
     * @return 访问URL
     */
    String getUrl(String bucketName, String objectKey);

    /**
     * 获取预览URL（临时签名URL）
     *
     * @param bucketName 桶名称
     * @param objectKey 对象key
     * @param expirySeconds 有效期（秒）
     * @return 预览URL
     */
    String getPresignedUrl(String bucketName, String objectKey, int expirySeconds);

    /**
     * 检查桶是否存在
     *
     * @param bucketName 桶名称
     * @return 是否存在
     */
    boolean bucketExists(String bucketName);

    /**
     * 创建桶
     *
     * @param bucketName 桶名称
     */
    void createBucket(String bucketName);

    /**
     * 生成对象key
     *
     * @param originalFilename 原始文件名
     * @param prefix 前缀路径
     * @return 对象key
     */
    String generateObjectKey(String originalFilename, String prefix);

    /**
     * 获取存储类型
     *
     * @return 存储类型
     */
    String getStorageType();
}