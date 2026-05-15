package com.nebula.blog.service;

import com.nebula.blog.vo.front.TagVO;

import java.util.List;

/**
 * 博客标签服务接口
 */
public interface BlogTagService {

    /**
     * 获取热门标签
     *
     * @param limit 返回数量
     * @return 标签列表
     */
    List<TagVO> getPopularTags(int limit);
}
