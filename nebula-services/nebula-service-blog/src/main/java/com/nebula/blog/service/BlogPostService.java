package com.nebula.blog.service;

import com.nebula.blog.dto.front.PostPageQuery;
import com.nebula.blog.vo.front.PostContentVO;
import com.nebula.blog.vo.front.PostListResponse;
import com.nebula.blog.vo.front.PostListVO;

import java.util.List;

/**
 * 博客文章服务接口
 */
public interface BlogPostService {

    /**
     * 分页查询文章列表
     *
     * @param query 分页查询参数
     * @return 文章列表响应
     */
    PostListResponse getArticles(PostPageQuery query);

    /**
     * 获取热门文章
     *
     * @param limit 返回数量
     * @return 热门文章列表
     */
    List<PostListVO> getHotArticles(int limit);

    /**
     * 获取文章详情
     *
     * @param slug 文章别名
     * @return 文章详情，不存在返回null
     */
    PostListVO getArticleDetail(String slug);

    /**
     * 获取文章内容
     *
     * @param slug 文章别名
     * @return 文章内容VO
     */
    PostContentVO getArticleContent(String slug);
}
