package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 关键结果：id 由前端生成，整组随目标一起保存
 */
@Data
public class GoalKeyResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 32)
    private String id;

    @NotBlank(message = "关键结果不能为空")
    @Size(max = 100)
    private String title;

    private boolean done;

    /**
     * 完成那天 YYYY-MM-DD；存在 JSON 列里，按字符串收
     */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String doneDate;

    /**
     * 挂的任务清单ID
     */
    @Size(max = 32)
    private String listId;
}
