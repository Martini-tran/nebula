package com.nebula.blog.front.controller;

import com.nebula.blog.service.BlogCategoryService;
import com.nebula.blog.vo.front.CategoryTreeVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类控制器（前端）
 */
@RestController
@RequestMapping("/front/categories")
@RequiredArgsConstructor
public class CategoryFrontController {

    private final BlogCategoryService categoryService;

    /**
     * 获取分类树
     *
     * @param parentId 父分类ID，可选
     */
    @GetMapping
    public R<List<CategoryTreeVO>> tree(@RequestParam(required = false) Long parentId) {
        return R.success(categoryService.getCategoryTree(parentId));
    }
}
