package com.nebula.blog.service;

import com.nebula.blog.dto.front.PostPageQuery;
import com.nebula.blog.vo.front.PostContentVO;
import com.nebula.blog.vo.front.PostListResponse;
import com.nebula.blog.vo.front.PostListVO;

import java.util.List;

public interface BlogPostService {

    PostListResponse getArticles(PostPageQuery query);

    List<PostListVO> getHotArticles(int limit);

    PostListVO getArticleDetail(String slug);

    PostContentVO getArticleContent(String slug);
}
