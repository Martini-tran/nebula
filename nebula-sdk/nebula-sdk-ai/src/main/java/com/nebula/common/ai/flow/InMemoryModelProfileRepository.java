package com.nebula.common.ai.flow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存模型档案仓储
 * SDK 默认实现，支持编程式注册。无存储依赖，便于无数据库场景与单元测试；业务侧可声明数据库实现覆盖。
 *
 * @author nebula
 */
public class InMemoryModelProfileRepository implements ModelProfileRepository {

    private final Map<String, ModelProfile> profiles = new ConcurrentHashMap<>();

    /**
     * 注册一个模型档案
     *
     * @param profile 模型档案
     * @return 当前仓储，便于链式注册
     */
    public InMemoryModelProfileRepository register(ModelProfile profile) {
        if (profile != null && profile.getProfileCode() != null) {
            profiles.put(profile.getProfileCode(), profile);
        }
        return this;
    }

    @Override
    public ModelProfile findByCode(String profileCode) {
        return profileCode == null ? null : profiles.get(profileCode);
    }
}
