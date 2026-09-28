package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceLedgerEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记账流水 Mapper
 */
@Mapper
public interface SpaceLedgerEntryMapper extends BaseMapper<SpaceLedgerEntry> {
}
