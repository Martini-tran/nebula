package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogSeries;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客系列Mapper接口
 */
@Mapper
public interface BlogSeriesMapper extends BaseMapper<BlogSeries> {
}
