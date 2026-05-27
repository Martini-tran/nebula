package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.TravelDestination;
import org.apache.ibatis.annotations.Mapper;

/**
 * 旅游目的地Mapper接口
 */
@Mapper
public interface TravelDestinationMapper extends BaseMapper<TravelDestination> {
}
