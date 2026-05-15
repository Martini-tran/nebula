package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogPostCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogPostCategoryMapper extends BaseMapper<BlogPostCategory> {
}
