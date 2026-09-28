package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 划线视图对象
 */
@Data
public class HighlightVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long itemId;

    private Integer para;

    private Integer start;

    private Integer end;

    private String text;

    private String color;

    private String note;

    private Long noteId;

    private Long taskId;

    private String taskTitle;

    private LocalDateTime createTime;
}
