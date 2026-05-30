package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceBookmarkImportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 书签导入任务 Mapper
 */
@Mapper
public interface SpaceBookmarkImportTaskMapper extends BaseMapper<SpaceBookmarkImportTask> {
}
