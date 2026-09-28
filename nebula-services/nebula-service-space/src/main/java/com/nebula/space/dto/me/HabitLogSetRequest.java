package com.nebula.space.dto.me;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 写入某习惯某天的值（覆盖）；value = 0 表示取消打卡
 */
@Data
public class HabitLogSetRequest {

    @NotNull(message = "value 不能为空")
    @Min(0)
    @Max(100000)
    private Integer value;

    /**
     * 不传则保留原备注
     */
    @Size(max = 500, message = "备注最长 500 字")
    private String note;
}
