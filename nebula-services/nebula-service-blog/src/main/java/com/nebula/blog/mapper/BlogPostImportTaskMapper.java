package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogPostImportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客文章导入任务Mapper接口
 */
@Mapper
public interface BlogPostImportTaskMapper extends BaseMapper<BlogPostImportTask> {
}
