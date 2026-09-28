package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 任务重复规则：daily 每天 / weekdays 工作日 / weekly 每周几 / monthly 每月同一天
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepeatRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "重复类型不能为空")
    @Pattern(regexp = "daily|weekdays|weekly|monthly", message = "不支持的重复类型")
    private String type;

    /**
     * weekly 时的星期：0=周日 … 6=周六；空则按任务当天每周一次
     */
    private List<@Min(0) @Max(6) Integer> days;
}
