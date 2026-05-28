package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.TravelTripDayFrontService;
import com.nebula.blog.vo.front.TravelTripDayVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行程日控制器（前端）
 */
@RestController
@RequestMapping("/front/travel/trip-days")
@RequiredArgsConstructor
public class TravelTripDayFrontController extends AbstractFrontController {

    private final TravelTripDayFrontService tripDayFrontService;

    /**
     * 列出指定游记下的行程日（仅当游记已发布且公开）
     */
    @GetMapping
    public R<List<TravelTripDayVO>> list(@RequestParam Long tripId) {
        return R.success(tripDayFrontService.listByTrip(tripId));
    }

    /**
     * 行程日详情
     */
    @GetMapping("/{id}")
    public R<TravelTripDayVO> detail(@PathVariable Long id) {
        TravelTripDayVO vo = tripDayFrontService.getDay(id);
        if (vo == null) {
            return R.fail(404, "行程日不存在");
        }
        return R.success(vo);
    }
}
