package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.BlogSearchIndexTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 博客搜索索引同步任务 Mapper
 */
@Mapper
public interface BlogSearchIndexTaskMapper extends BaseMapper<BlogSearchIndexTask> {
}
