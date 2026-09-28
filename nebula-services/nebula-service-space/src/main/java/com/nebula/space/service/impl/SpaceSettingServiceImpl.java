package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.entity.SpaceUserSetting;
import com.nebula.space.mapper.SpaceUserSettingMapper;
import com.nebula.space.service.SpaceSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个人空间偏好服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceSettingServiceImpl implements SpaceSettingService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> SETTINGS_MAP = new TypeReference<>() {
    };
    /**
     * 偏好只是几十个开关和数字，超过这个长度多半是传错了东西；按字符算，全是中文也不超过 text 列的 64KB
     */
    static final int MAX_LENGTH = 8 * 1024;

    private final SpaceUserSettingMapper settingMapper;

    @Override
    public Map<String, Object> get() {
        SpaceUserSetting row = find(requireUserId());
        if (row == null || !StringUtils.hasText(row.getSettings())) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(row.getSettings(), SETTINGS_MAP);
        } catch (JsonProcessingException e) {
            // 存坏了就当没存过，前端回到默认值，下次保存覆盖掉
            return new LinkedHashMap<>();
        }
    }

    @Override
    public void save(Map<String, Object> settings) {
        if (settings == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "偏好不能为空");
        }
        Long userId = requireUserId();
        String json;
        try {
            json = JSON.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "偏好格式不正确");
        }
        if (json.length() > MAX_LENGTH) {
            throw new BizException(HttpStatus.BAD_REQUEST, "偏好内容过大");
        }
        SpaceUserSetting existing = find(userId);
        if (existing == null) {
            SpaceUserSetting row = new SpaceUserSetting();
            row.setUserId(userId);
            row.setSettings(json);
            try {
                settingMapper.insert(row);
                return;
            } catch (DuplicateKeyException e) {
                // 两个标签页同时第一次保存：另一份已插入，改为覆盖它
                existing = find(userId);
                if (existing == null) {
                    throw e;
                }
            }
        }
        existing.setSettings(json);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        existing.setUpdateTime(LocalDateTime.now());
        settingMapper.updateById(existing);
    }

    private SpaceUserSetting find(Long userId) {
        return settingMapper.selectOne(
                new LambdaQueryWrapper<SpaceUserSetting>().eq(SpaceUserSetting::getUserId, userId)
        );
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }
}
