package com.nebula.space.vo.me;

import com.nebula.space.dto.me.RepeatRule;
import com.nebula.space.dto.me.SubTask;
import com.nebula.space.dto.me.TaskSource;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务视图对象
 */
@Data
public class TaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private Long listId;

    /**
     * 日期，空为收件箱
     */
    private LocalDate dueDate;

    /**
     * HH:mm，空为全天
     */
    private String dueTime;

    private Integer priority;

    private Boolean done;

    private LocalDateTime doneTime;

    private Integer estimateMin;

    private Integer remindBefore;

    private RepeatRule repeat;

    private List<SubTask> subtasks;

    private TaskSource source;

    private String note;

    private LocalDateTime createTime;
}
