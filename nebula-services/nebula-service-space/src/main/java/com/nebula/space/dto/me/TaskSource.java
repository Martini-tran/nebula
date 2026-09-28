package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务来源：从笔记、会议、书签、稍后读、人物卡转来的任务记下出处
 */
@Data
public class TaskSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Pattern(regexp = "note|meeting|bookmark|reading|person", message = "不支持的来源类型")
    private String type;

    /**
     * 来源ID：其他模块可能还在 mock，ID 不一定是数字，按字符串存
     */
    @NotBlank
    @Size(max = 64)
    private String id;

    @Size(max = 200)
    private String label;
}
