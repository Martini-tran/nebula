package com.nebula.space.dto.me;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 完成 / 撤销完成
 */
@Data
public class TaskDoneRequest {

    @NotNull(message = "done 不能为空")
    private Boolean done;
}
