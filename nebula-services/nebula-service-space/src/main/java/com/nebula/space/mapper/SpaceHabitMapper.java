package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceHabit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 习惯 Mapper
 */
@Mapper
public interface SpaceHabitMapper extends BaseMapper<SpaceHabit> {
}
