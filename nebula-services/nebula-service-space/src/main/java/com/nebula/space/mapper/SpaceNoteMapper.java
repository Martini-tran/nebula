package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 随手记 Mapper
 */
@Mapper
public interface SpaceNoteMapper extends BaseMapper<SpaceNote> {
}
