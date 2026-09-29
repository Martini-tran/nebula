package com.nebula.scribe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.scribe.entity.ScribeLoreEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 写作台设定条目 Mapper
 */
@Mapper
public interface ScribeLoreEntryMapper extends BaseMapper<ScribeLoreEntry> {
}
