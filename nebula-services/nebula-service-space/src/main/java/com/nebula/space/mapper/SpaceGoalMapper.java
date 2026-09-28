package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceGoal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 年度目标 Mapper
 */
@Mapper
public interface SpaceGoalMapper extends BaseMapper<SpaceGoal> {
}
