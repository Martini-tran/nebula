package com.nebula.space.dto.me;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改划线：改颜色、写批注、记下转出的随手记 / 任务；不传的字段不动
 */
@Data
public class HighlightSaveRequest {

    @Pattern(regexp = "yellow|blue", message = "划线颜色只能是 yellow 或 blue")
    private String color;

    @Size(max = 1000, message = "批注最长 1000 字")
    private String note;

    /**
     * 转出的随手记，得是自己的
     */
    private Long noteId;

    /**
     * 转出的任务，得是自己的
     */
    private Long taskId;

    @Size(max = 200, message = "任务标题最长 200 字")
    private String taskTitle;
}
