package com.nebula.space.util;

import com.nebula.space.dto.me.RepeatRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatRulesTest {

    private static RepeatRule rule(String type, Integer... days) {
        RepeatRule r = new RepeatRule();
        r.setType(type);
        r.setDays(days.length == 0 ? null : List.of(days));
        return r;
    }

    /** 2026-09-25 是周五 */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 25);

    @Test
    void daily() {
        assertEquals(FRIDAY.plusDays(1), RepeatRules.next(rule("daily"), FRIDAY));
    }

    @Test
    void weekdaysSkipsWeekend() {
        assertEquals(LocalDate.of(2026, 9, 28), RepeatRules.next(rule("weekdays"), FRIDAY));
    }

    @Test
    void weeklyPicksNextChosenDay() {
        // 周一、周三：周五之后是下周一
        assertEquals(LocalDate.of(2026, 9, 28), RepeatRules.next(rule("weekly", 1, 3), FRIDAY));
        // 周六（6）：第二天
        assertEquals(LocalDate.of(2026, 9, 26), RepeatRules.next(rule("weekly", 6), FRIDAY));
        // 周日（0）
        assertEquals(LocalDate.of(2026, 9, 27), RepeatRules.next(rule("weekly", 0), FRIDAY));
    }

    @Test
    void weeklyWithoutDaysKeepsSameWeekday() {
        assertEquals(FRIDAY.plusWeeks(1), RepeatRules.next(rule("weekly"), FRIDAY));
    }

    @Test
    void monthlyClampsToMonthEnd() {
        assertEquals(LocalDate.of(2026, 2, 28), RepeatRules.next(rule("monthly"), LocalDate.of(2026, 1, 31)));
        assertEquals(LocalDate.of(2026, 10, 25), RepeatRules.next(rule("monthly"), FRIDAY));
    }
}
