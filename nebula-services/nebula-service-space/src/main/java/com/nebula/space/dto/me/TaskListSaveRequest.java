package com.nebula.space.dto.me;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 清单新建 / 修改：字段为空表示不修改，新建时名称必填
 */
@Data
public class TaskListSaveRequest {

    @Size(max = 50, message = "清单名称最长 50 字")
    private String name;

    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "颜色格式应为 #RRGGBB")
    private String color;
}
