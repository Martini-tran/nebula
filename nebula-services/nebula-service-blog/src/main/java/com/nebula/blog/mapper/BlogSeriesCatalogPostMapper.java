package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogSeriesCatalogPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 目录文章关联Mapper接口
 */
@Mapper
public interface BlogSeriesCatalogPostMapper extends BaseMapper<BlogSeriesCatalogPost> {
}
