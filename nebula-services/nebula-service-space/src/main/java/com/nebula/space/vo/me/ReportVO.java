package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日报周报视图对象
 */
@Data
public class ReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * day / week
     */
    private String type;

    /**
     * 日报为当天，周报为这周第一天
     */
    private LocalDate date;

    private String content;

    private LocalDateTime updateTime;
}
