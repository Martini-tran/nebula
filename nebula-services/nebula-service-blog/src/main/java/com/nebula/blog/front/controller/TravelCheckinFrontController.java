package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.TravelCheckinFrontService;
import com.nebula.blog.vo.front.TravelCheckinVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 打卡点控制器（前端）
 */
@RestController
@RequestMapping("/front/travel/checkins")
@RequiredArgsConstructor
public class TravelCheckinFrontController extends AbstractFrontController {

    private final TravelCheckinFrontService checkinFrontService;

    /**
     * 列出指定行程日下的打卡点
     */
    @GetMapping
    public R<List<TravelCheckinVO>> list(@RequestParam Long tripDayId) {
        return R.success(checkinFrontService.listByTripDay(tripDayId));
    }

    /**
     * 打卡点详情
     */
    @GetMapping("/{id}")
    public R<TravelCheckinVO> detail(@PathVariable Long id) {
        TravelCheckinVO vo = checkinFrontService.getCheckin(id);
        if (vo == null) {
            return R.fail(404, "打卡点不存在");
        }
        return R.success(vo);
    }
}
