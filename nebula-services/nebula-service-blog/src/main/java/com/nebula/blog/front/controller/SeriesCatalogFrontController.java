package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.BlogSeriesCatalogFrontService;
import com.nebula.blog.vo.front.SeriesCatalogNodeVO;
import com.nebula.blog.vo.front.SeriesChapterVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系列目录控制器（前端）
 */
@RestController
@RequestMapping("/front/series/catalogs")
@RequiredArgsConstructor
public class SeriesCatalogFrontController extends AbstractFrontController {

    private final BlogSeriesCatalogFrontService catalogFrontService;

    /**
     * 获取指定系列的目录树
     */
    @GetMapping("/tree")
    public R<List<SeriesCatalogNodeVO>> tree(@RequestParam Long seriesId) {
        return R.success(catalogFrontService.getCatalogTree(seriesId));
    }

    /**
     * 查询目录节点下的文章列表
     */
    @GetMapping("/{id}/posts")
    public R<List<SeriesChapterVO>> listPosts(@PathVariable Long id) {
        return R.success(catalogFrontService.listPostsByCatalog(id));
    }
}
