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
 * 习惯频率：daily 每天 / weekly_n 每周 n 次（哪天都行）/ weekdays 指定星期几
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HabitFreq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "频率类型不能为空")
    @Pattern(regexp = "daily|weekly_n|weekdays", message = "不支持的频率类型")
    private String type;

    /**
     * weekly_n：每周几次
     */
    @Min(1)
    @Max(7)
    private Integer n;

    /**
     * weekdays：0=周日 … 6=周六
     */
    private List<@Min(0) @Max(6) Integer> days;
}
