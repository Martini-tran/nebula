package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper
 */
@Mapper
public interface SpaceTaskMapper extends BaseMapper<SpaceTask> {
}
