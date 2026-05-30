package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogContentVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客内容版本（快照）Mapper
 */
@Mapper
public interface BlogContentVersionMapper extends BaseMapper<BlogContentVersion> {
}
