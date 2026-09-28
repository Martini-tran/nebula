package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceTaskList;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务清单 Mapper
 */
@Mapper
public interface SpaceTaskListMapper extends BaseMapper<SpaceTaskList> {
}
