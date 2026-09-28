package com.nebula.space.dto.me;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上报一轮专注：计时以前端为准，一轮结束（完成或放弃）时上报一次
 */
@Data
public class FocusSessionCreateRequest {

    /**
     * 不挂任务时为 null
     */
    private Long taskId;

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题最长 200 字")
    private String taskTitle;

    /**
     * yyyy-MM-dd HH:mm:ss，也接受 ISO 写法
     */
    @NotBlank(message = "开始时间不能为空")
    private String startedAt;

    @NotBlank(message = "结束时间不能为空")
    private String endedAt;

    @NotNull(message = "计划分钟不能为空")
    @Min(1)
    @Max(1440)
    private Integer plannedMin;

    @NotNull(message = "实际分钟不能为空")
    @Min(0)
    @Max(1440)
    private Integer actualMin;

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "done|abandoned", message = "状态只能是 done 或 abandoned")
    private String status;

    @Min(0)
    @Max(1000)
    private Integer interruptions;
}
