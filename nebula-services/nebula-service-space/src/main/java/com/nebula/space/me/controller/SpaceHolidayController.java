package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.holiday.HolidayCalendar;
import com.nebula.space.vo.me.HolidayYearVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 前台法定节假日：日历标「休 / 班」、周末底色按调休修正
 */
@RestController
@RequestMapping("/me/holidays")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceHolidayController {

    private final HolidayCalendar holidayCalendar;

    /**
     * 某年的放假与调休上班日，不传为今年；还没公布的年份为空
     */
    @GetMapping
    public R<HolidayYearVO> year(@RequestParam(required = false) Integer year) {
        return R.success(holidayCalendar.year(year == null ? LocalDate.now().getYear() : year));
    }
}
