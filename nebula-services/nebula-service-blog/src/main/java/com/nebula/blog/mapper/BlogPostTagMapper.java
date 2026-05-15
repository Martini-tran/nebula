package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogPostTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客文章-标签关联Mapper接口
 */
@Mapper
public interface BlogPostTagMapper extends BaseMapper<BlogPostTag> {
}
