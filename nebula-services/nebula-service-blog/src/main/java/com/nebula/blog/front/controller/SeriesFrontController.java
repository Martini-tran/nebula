package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.SeriesPageQuery;
import com.nebula.blog.service.BlogSeriesService;
import com.nebula.blog.vo.front.SeriesDetailVO;
import com.nebula.blog.vo.front.SeriesListResponse;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系列控制器（前端）
 */
@RestController
@RequestMapping("/front/series")
@RequiredArgsConstructor
public class SeriesFrontController extends AbstractFrontController {

    private final BlogSeriesService seriesService;

    /**
     * 分页查询系列列表
     */
    @GetMapping
    public R<SeriesListResponse> list(SeriesPageQuery query) {
        return R.success(seriesService.getSeriesList(query));
    }

    /**
     * 按 slug 查询系列详情
     */
    @GetMapping("/{slug}")
    public R<SeriesDetailVO> detail(@PathVariable String slug) {
        SeriesDetailVO vo = seriesService.getSeriesDetail(slug);
        if (vo == null) {
            return R.fail(404, "系列不存在");
        }
        return R.success(vo);
    }
}
