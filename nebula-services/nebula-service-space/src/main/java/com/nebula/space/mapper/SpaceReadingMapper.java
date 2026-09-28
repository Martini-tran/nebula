package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceReading;
import org.apache.ibatis.annotations.Mapper;

/**
 * 稍后读文章 Mapper
 */
@Mapper
public interface SpaceReadingMapper extends BaseMapper<SpaceReading> {
}
