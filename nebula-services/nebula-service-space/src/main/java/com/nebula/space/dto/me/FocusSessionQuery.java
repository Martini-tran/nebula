package com.nebula.space.dto.me;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 专注记录查询：按开始日期筛选（两端都含），可只看某个任务
 */
@Data
public class FocusSessionQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    private Long taskId;
}
