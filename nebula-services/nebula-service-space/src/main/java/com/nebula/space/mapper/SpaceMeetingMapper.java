package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceMeeting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议记录 Mapper
 */
@Mapper
public interface SpaceMeetingMapper extends BaseMapper<SpaceMeeting> {
}
