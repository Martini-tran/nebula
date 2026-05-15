package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客分类Mapper接口
 */
@Mapper
public interface BlogCategoryMapper extends BaseMapper<BlogCategory> {
}
