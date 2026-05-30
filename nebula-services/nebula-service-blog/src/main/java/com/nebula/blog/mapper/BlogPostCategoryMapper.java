package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogPostCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客文章-分类关联Mapper接口
 */
@Mapper
public interface BlogPostCategoryMapper extends BaseMapper<BlogPostCategory> {
}
