package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.BlogTagService;
import com.nebula.blog.vo.front.TagVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签控制器（前端）
 */
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagFrontController extends AbstractFrontController {

    private final BlogTagService tagService;

    /**
     * 获取热门标签
     *
     * @param limit 返回数量，默认20
     */
    @GetMapping
    public R<List<TagVO>> popular(@RequestParam(defaultValue = "20") int limit) {
        return R.success(tagService.getPopularTags(limit));
    }
}
