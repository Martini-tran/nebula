package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.AiMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI长期记忆Mapper接口
 */
@Mapper
public interface AiMemoryMapper extends BaseMapper<AiMemory> {
}
