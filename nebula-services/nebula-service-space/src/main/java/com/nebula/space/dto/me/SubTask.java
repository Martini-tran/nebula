package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 子任务：id 由前端生成，整组随任务一起保存
 */
@Data
public class SubTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 32)
    private String id;

    @NotBlank(message = "子任务标题不能为空")
    @Size(max = 200, message = "子任务标题最长 200 字")
    private String title;

    private boolean done;
}
