package com.nebula.common.oss.api;

/**
 * 对象存储服务工厂接口
 *
 * @author nebula
 */
public interface ObjectStorageFactory {

    /**
     * 获取默认存储服务
     *
     * @return 存储服务
     */
    ObjectStorageService getDefaultService();

    /**
     * 根据存储类型获取存储服务
     *
     * @param storageType 存储类型
     * @return 存储服务
     */
    ObjectStorageService getService(StorageType storageType);

    /**
     * 根据桶名称获取存储服务（桶可能配置在不同的存储服务上）
     *
     * @param bucketName 桶名称
     * @return 存储服务
     */
    ObjectStorageService getServiceByBucket(String bucketName);

    /**
     * 获取当前支持的存储类型
     *
     * @return 存储类型
     */
    StorageType getSupportedStorageType();
}