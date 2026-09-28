package com.nebula.space.util;

import com.nebula.space.dto.me.RepeatRule;

import java.time.LocalDate;
import java.util.List;

/**
 * 重复规则：算下一次日期，与前端 utils/repeat.ts 的 nextOccurrence 一致
 */
public final class RepeatRules {

    private RepeatRules() {
    }

    /**
     * from 之后（不含 from）的下一次日期
     */
    public static LocalDate next(RepeatRule rule, LocalDate from) {
        return switch (rule.getType()) {
            case "weekdays" -> {
                LocalDate next = from.plusDays(1);
                while (weekday(next) == 0 || weekday(next) == 6) {
                    next = next.plusDays(1);
                }
                yield next;
            }
            case "weekly" -> {
                // 没选星期就按原来那天每周一次
                List<Integer> days = rule.getDays() == null || rule.getDays().isEmpty()
                        ? List.of(weekday(from)) : rule.getDays();
                LocalDate next = from.plusDays(1);
                for (int i = 0; i < 7 && !days.contains(weekday(next)); i++) {
                    next = next.plusDays(1);
                }
                yield next;
            }
            // plusMonths 在小月自动落到月底（1/31 → 2/28）
            case "monthly" -> from.plusMonths(1);
            default -> from.plusDays(1);
        };
    }

    /**
     * 0=周日 … 6=周六，与前端 weekdayOf 一致
     */
    private static int weekday(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7;
    }
}
