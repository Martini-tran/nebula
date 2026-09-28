package com.nebula.space.dto.me;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 议程条目：id 由前端生成，整组随会议一起保存
 */
@Data
public class MeetingAgendaItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 32)
    private String id;

    @NotBlank(message = "议题不能为空")
    @Size(max = 200)
    private String title;

    /**
     * 时间预算（分钟）
     */
    @Min(0)
    private int budgetMin;

    /**
     * 实际用时（秒）
     */
    @Min(0)
    private int usedSec;
}
