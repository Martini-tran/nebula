package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.dto.front.TravelTripPageQuery;
import com.nebula.blog.service.TravelTripFrontService;
import com.nebula.blog.vo.front.TravelTripDetailVO;
import com.nebula.blog.vo.front.TravelTripListResponse;
import com.nebula.blog.vo.front.TravelTripListVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 游记控制器（前端）
 */
@RestController
@RequestMapping("/front/travel/trips")
@RequiredArgsConstructor
public class TravelTripFrontController extends AbstractFrontController {

    private final TravelTripFrontService tripService;

    /**
     * 分页查询游记列表
     */
    @GetMapping
    public R<TravelTripListResponse> list(TravelTripPageQuery query) {
        return R.success(tripService.listTrips(query));
    }

    /**
     * 关键词搜索游记
     */
    @GetMapping("/search")
    public R<TravelTripListResponse> search(TravelTripPageQuery query) {
        return R.success(tripService.searchTrips(query));
    }

    /**
     * 热门游记
     */
    @GetMapping("/hot")
    public R<List<TravelTripListVO>> hot(@RequestParam(defaultValue = "5") int limit) {
        return R.success(tripService.getHotTrips(limit));
    }

    /**
     * 游记详情
     *
     * @param slug 游记别名
     */
    @GetMapping("/{slug}")
    public R<TravelTripDetailVO> detail(@PathVariable String slug) {
        TravelTripDetailVO vo = tripService.getTripDetail(slug);
        if (vo == null) {
            return R.fail(404, "游记不存在");
        }
        return R.success(vo);
    }
}
