package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 纪念日视图对象：下一次在哪天、还有几天由前端按类型和历法算
 */
@Data
public class AnniversaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String icon;

    private String type;

    private LocalDate date;

    private String calendar;

    private Integer lunarMonth;

    private Integer lunarDay;

    private Integer remindDays;

    private Boolean createTask;

    private String taskTitle;

    private LocalDate taskFor;

    private String fileId;

    private String note;

    private String tag;

    private LocalDateTime createTime;
}
