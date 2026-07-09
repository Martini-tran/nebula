package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogPostImportItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客文章导入明细Mapper接口
 */
@Mapper
public interface BlogPostImportItemMapper extends BaseMapper<BlogPostImportItem> {
}
