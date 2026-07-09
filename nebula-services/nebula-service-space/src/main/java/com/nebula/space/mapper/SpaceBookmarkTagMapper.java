package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceBookmarkTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 书签-标签关联 Mapper
 */
@Mapper
public interface SpaceBookmarkTagMapper extends BaseMapper<SpaceBookmarkTag> {
}
