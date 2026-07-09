package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceBookmarkExportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 书签导出任务 Mapper
 */
@Mapper
public interface SpaceBookmarkExportTaskMapper extends BaseMapper<SpaceBookmarkExportTask> {
}
