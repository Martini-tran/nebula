package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.service.SpaceOverviewService;
import com.nebula.space.vo.me.CalendarVO;
import com.nebula.space.vo.me.TodayVO;
import com.nebula.space.vo.me.WeekReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 前台按天看的聚合视图：「今天」页与日历各一次请求取齐
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceOverviewController {

    private final SpaceOverviewService overviewService;

    /**
     * 「今天」页：当天任务（含过期、本周完成）、会议、笔记、收藏
     */
    @GetMapping("/today")
    public R<TodayVO> today(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return R.success(overviewService.today(date, weekStart));
    }

    /**
     * 日历：起止日期（含）内的任务、会议、习惯打卡、日记、专注
     */
    @GetMapping("/calendar")
    public R<CalendarVO> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return R.success(overviewService.calendar(from, to, today));
    }

    /**
     * 周回顾：这一周与上一周的数据，外加没做完的任务与上周的周报
     */
    @GetMapping("/review/weekly")
    public R<WeekReviewVO> weekReview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        return R.success(overviewService.weekReview(start));
    }
}
