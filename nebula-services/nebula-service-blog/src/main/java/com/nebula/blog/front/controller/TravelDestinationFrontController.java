package com.nebula.blog.front.controller;

import com.nebula.blog.controller.AbstractFrontController;
import com.nebula.blog.service.TravelDestinationFrontService;
import com.nebula.blog.vo.front.TravelDestinationSummaryVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 旅游目的地控制器（前端）
 */
@RestController
@RequestMapping("/front/travel/destinations")
@RequiredArgsConstructor
public class TravelDestinationFrontController extends AbstractFrontController {

    private final TravelDestinationFrontService destinationService;

    /**
     * 用于前台筛选的目的地列表
     */
    @GetMapping
    public R<List<TravelDestinationSummaryVO>> list(@RequestParam(defaultValue = "30") int limit) {
        return R.success(destinationService.listChips(limit));
    }
}
