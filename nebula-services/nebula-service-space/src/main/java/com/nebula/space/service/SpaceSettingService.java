package com.nebula.space.service;

import java.util.Map;

/**
 * 个人空间偏好服务：整份 JSON 读写，字段由前端定义（types/settings.ts），后端不逐项解释
 *
 * <p>其他模块需要的偏好（如随手记保留天数）由前端随请求带参，后端不从这里读。</p>
 */
public interface SpaceSettingService {

    /**
     * 当前用户的偏好；从没保存过返回空对象，前端据此用默认值
     */
    Map<String, Object> get();

    /**
     * 整份覆盖
     */
    void save(Map<String, Object> settings);
}
