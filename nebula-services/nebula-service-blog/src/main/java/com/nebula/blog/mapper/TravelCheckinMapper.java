package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.TravelCheckin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行程打卡点Mapper接口
 */
@Mapper
public interface TravelCheckinMapper extends BaseMapper<TravelCheckin> {
}
