package com.nebula.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.space.entity.SpaceLedgerBudget;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记账月预算 Mapper
 */
@Mapper
public interface SpaceLedgerBudgetMapper extends BaseMapper<SpaceLedgerBudget> {
}
