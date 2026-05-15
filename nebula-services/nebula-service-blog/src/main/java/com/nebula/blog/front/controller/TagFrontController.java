package com.nebula.blog.front.controller;

import com.nebula.blog.service.BlogTagService;
import com.nebula.blog.vo.front.TagVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/front/tags")
@RequiredArgsConstructor
public class TagFrontController {

    private final BlogTagService tagService;

    @GetMapping
    public R<List<TagVO>> popular(@RequestParam(defaultValue = "20") int limit) {
        return R.success(tagService.getPopularTags(limit));
    }
}
