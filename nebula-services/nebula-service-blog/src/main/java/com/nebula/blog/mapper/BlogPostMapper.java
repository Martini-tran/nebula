package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客文章Mapper接口
 */
@Mapper
public interface BlogPostMapper extends BaseMapper<BlogPost> {
}
