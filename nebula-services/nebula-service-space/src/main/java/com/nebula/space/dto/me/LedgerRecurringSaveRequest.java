package com.nebula.space.dto.me;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建 / 修改周期账单。新建时名称、金额、收支、分类、几号必填；修改时不传的字段不动
 */
@Data
public class LedgerRecurringSaveRequest {

    @Size(max = 100, message = "名称最长 100 字")
    private String note;

    @Min(value = 1, message = "金额要大于 0")
    @Max(value = 99_999_999_999L, message = "金额过大")
    private Long amount;

    @Pattern(regexp = "out|in", message = "收支只能是 out 或 in")
    private String direction;

    private Long categoryId;

    /**
     * 每月几号；只到 28，免得小月没有这天
     */
    @Min(1)
    @Max(28)
    private Integer day;

    private Boolean active;

    /**
     * 从哪个月开始 YYYY-MM，只在新建时有用，不传为本月
     */
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "月份格式应为 YYYY-MM")
    private String startMonth;
}
