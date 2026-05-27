package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.TravelTrip;
import org.apache.ibatis.annotations.Mapper;

/**
 * 旅行游记Mapper接口
 */
@Mapper
public interface TravelTripMapper extends BaseMapper<TravelTrip> {
}
