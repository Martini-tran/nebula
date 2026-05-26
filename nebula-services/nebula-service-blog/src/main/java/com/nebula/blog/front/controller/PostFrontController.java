package com.nebula.blog.front.controller;

import com.nebula.blog.dto.front.PostPageQuery;
import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.BlogPostService;
import com.nebula.blog.vo.front.PostContentVO;
import com.nebula.blog.vo.front.PostListResponse;
import com.nebula.blog.vo.front.PostListVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章控制器（前端）
 */
@RestController
@RequestMapping("/front/articles")
@RequiredArgsConstructor
public class PostFrontController extends AbstractFrontController {

    private final BlogPostService postService;

    /**
     * 分页查询文章列表
     */
    @GetMapping
    public R<PostListResponse> list(PostPageQuery query) {
        return R.success(postService.getArticles(query));
    }

    /**
     * 搜索文章
     */
    @GetMapping("/search")
    public R<PostListResponse> search(PostPageQuery query) {
        return R.success(postService.searchArticles(query));
    }

    /**
     * 获取热门文章
     *
     * @param limit 返回数量，默认5
     */
    @GetMapping("/hot")
    public R<List<PostListVO>> hot(@RequestParam(defaultValue = "5") int limit) {
        return R.success(postService.getHotArticles(limit));
    }

    /**
     * 获取文章详情
     *
     * @param slug 文章别名
     */
    @GetMapping("/{slug}")
    public R<PostListVO> detail(@PathVariable String slug) {
        PostListVO vo = postService.getArticleDetail(slug);
        if (vo == null) {
            return R.fail(404, "文章不存在");
        }
        return R.success(vo);
    }

    /**
     * 获取文章内容
     *
     * @param slug 文章别名
     */
    @GetMapping("/{slug}/content")
    public R<PostContentVO> content(@PathVariable String slug) {
        return R.success(postService.getArticleContent(slug));
    }
}
