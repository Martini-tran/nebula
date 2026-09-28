package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务清单视图对象
 */
@Data
public class TaskListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String color;
}
