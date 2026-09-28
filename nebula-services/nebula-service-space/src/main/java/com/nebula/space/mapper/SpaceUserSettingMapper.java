package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceUserSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人空间偏好 Mapper
 */
@Mapper
public interface SpaceUserSettingMapper extends BaseMapper<SpaceUserSetting> {
}
