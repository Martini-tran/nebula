package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客标签Mapper接口
 */
@Mapper
public interface BlogTagMapper extends BaseMapper<BlogTag> {
}
