package com.nebula.space.dto.me;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 习惯新建 / 局部保存：字段为空表示不修改，新建时名称必填
 */
@Data
public class HabitSaveRequest {

    @Size(max = 50, message = "名称最长 50 字")
    private String name;

    /**
     * Iconify 名称（集合:名字），如 lucide:glass-water
     */
    @Size(max = 64)
    @Pattern(regexp = "[a-z0-9-]+:[a-z0-9-]+", message = "图标应为 Iconify 名称，如 lucide:glass-water")
    private String icon;

    @Pattern(regexp = "check|count|duration", message = "不支持的习惯类型")
    private String kind;

    @Min(value = 1, message = "目标值至少为 1")
    @Max(10000)
    private Integer target;

    @Size(max = 16)
    private String unit;

    @Valid
    private HabitFreq freq;

    @Size(max = 5, message = "提醒最多 5 个")
    private List<@Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "提醒时间格式应为 HH:mm") String> reminders;

    private Boolean fromFocus;

    private Boolean archived;

    private Integer sortOrder;
}
