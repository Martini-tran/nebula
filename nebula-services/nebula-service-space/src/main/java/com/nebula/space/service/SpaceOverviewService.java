package com.nebula.space.service;

import com.nebula.space.vo.me.CalendarVO;
import com.nebula.space.vo.me.TodayVO;
import com.nebula.space.vo.me.WeekReviewVO;

import java.time.LocalDate;

/**
 * 按天看的聚合视图：「今天」页与日历，一次取齐几个模块的数据，只查当前登录用户自己的
 *
 * <p>「今天」由前端传入（浏览器所在时区的今天），与前端分组的口径一致。</p>
 */
public interface SpaceOverviewService {

    /**
     * @param date      今天
     * @param weekStart 本周第一天（前端按「一周从周几开始」算好），用来带上本周完成的任务
     */
    TodayVO today(LocalDate date, LocalDate weekStart);

    /**
     * @param from  起（含）
     * @param to    止（含），整段最多 100 天
     * @param today 今天，用来带上过期没做完的任务
     */
    CalendarVO calendar(LocalDate from, LocalDate to, LocalDate today);

    /**
     * 周回顾
     *
     * @param start 这一周的第一天（前端按「一周从周几开始」算好）；会连上一周一起取，好做对比
     */
    WeekReviewVO weekReview(LocalDate start);
}
