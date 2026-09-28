package com.nebula.space.vo.me;

import com.nebula.space.dto.me.HabitFreq;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 习惯视图对象
 */
@Data
public class HabitVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String icon;

    private String kind;

    private Integer target;

    private String unit;

    private HabitFreq freq;

    private List<String> reminders;

    private Boolean fromFocus;

    private Boolean archived;

    private Integer sortOrder;

    private LocalDateTime createTime;
}
