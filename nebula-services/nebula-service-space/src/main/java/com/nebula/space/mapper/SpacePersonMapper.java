package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpacePerson;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人物卡 Mapper
 */
@Mapper
public interface SpacePersonMapper extends BaseMapper<SpacePerson> {
}
