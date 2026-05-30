package com.nebula.common.oss.service;

import com.nebula.common.oss.api.ObjectStorageFactory;
import com.nebula.common.oss.api.ObjectStorageService;
import com.nebula.common.oss.api.StorageType;
import lombok.RequiredArgsConstructor;

/**
 * 单一存储服务工厂实现（适用于只配置一种存储服务的场景）
 *
 * @author nebula
 */
@RequiredArgsConstructor
public class SingleStorageFactory implements ObjectStorageFactory {

    private final ObjectStorageService objectStorageService;
    private final StorageType storageType;

    @Override
    public ObjectStorageService getDefaultService() {
        return objectStorageService;
    }

    @Override
    public ObjectStorageService getService(StorageType storageType) {
        if (this.storageType == storageType) {
            return objectStorageService;
        }
        throw new UnsupportedOperationException("不支持的存储类型: " + storageType);
    }

    @Override
    public ObjectStorageService getServiceByBucket(String bucketName) {
        return objectStorageService;
    }

    @Override
    public StorageType getSupportedStorageType() {
        return storageType;
    }
}