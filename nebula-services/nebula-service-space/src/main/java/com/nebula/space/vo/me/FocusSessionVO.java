package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 专注记录视图对象
 */
@Data
public class FocusSessionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long taskId;

    private String taskTitle;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Integer plannedMin;

    private Integer actualMin;

    private String status;

    private Integer interruptions;
}
