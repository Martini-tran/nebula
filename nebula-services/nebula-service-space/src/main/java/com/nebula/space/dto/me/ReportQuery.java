package com.nebula.space.dto.me;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 日报周报列表查询：不传类型为全部，日期区间按 period_start 筛选（两端都含）
 */
@Data
public class ReportQuery {

    /**
     * day / week
     */
    private String type;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
