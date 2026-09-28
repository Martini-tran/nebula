package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录视图对象
 */
@Data
public class HabitLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long habitId;

    private LocalDate date;

    private Integer value;

    private String note;

    /**
     * 事后补打卡
     */
    private Boolean backfilled;

    /**
     * 最近一次写入时间
     */
    private LocalDateTime time;
}
