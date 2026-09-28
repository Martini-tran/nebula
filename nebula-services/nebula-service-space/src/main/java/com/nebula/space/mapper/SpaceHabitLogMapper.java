package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceHabitLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 习惯打卡 Mapper
 */
@Mapper
public interface SpaceHabitLogMapper extends BaseMapper<SpaceHabitLog> {
}
