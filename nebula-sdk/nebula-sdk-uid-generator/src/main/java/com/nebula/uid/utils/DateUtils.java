package com.nebula.uid.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期工具类，基于 {@link java.time} 提供格式化与解析方法。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)，已用 java.time API 重写。</p>
 *
 * @author nebula
 */
public abstract class DateUtils {

    public static final String DAY_PATTERN = "yyyy-MM-dd";
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATETIME_MS_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern(DAY_PATTERN);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_PATTERN);

    /**
     * 按 'yyyy-MM-dd' 解析日期为 {@link Date}
     */
    public static Date parseByDayPattern(String str) {
        LocalDate date = LocalDate.parse(str, DAY_FORMATTER);
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 按 'yyyy-MM-dd' 格式化 {@link Date}
     */
    public static String formatByDayPattern(Date date) {
        if (date == null) {
            return null;
        }
        return toLocalDateTime(date).format(DAY_FORMATTER);
    }

    /**
     * 按 'yyyy-MM-dd HH:mm:ss' 格式化 {@link Date}
     */
    public static String formatByDateTimePattern(Date date) {
        if (date == null) {
            return null;
        }
        return toLocalDateTime(date).format(DATETIME_FORMATTER);
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
