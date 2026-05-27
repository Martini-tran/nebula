package com.nebula.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.TravelTripBlogPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游记与博客文章关联Mapper接口
 */
@Mapper
public interface TravelTripBlogPostMapper extends BaseMapper<TravelTripBlogPost> {
}
