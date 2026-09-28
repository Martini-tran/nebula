package com.nebula.space.dto.me;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 记一笔 / 改一笔。新建时金额、收支、分类、日期必填；修改时不传的字段不动（这几个字段都不能清空）
 */
@Data
public class LedgerEntrySaveRequest {

    /**
     * 分，正数
     */
    @Min(value = 1, message = "金额要大于 0")
    @Max(value = 99_999_999_999L, message = "金额过大")
    private Long amount;

    @Pattern(regexp = "out|in", message = "收支只能是 out 或 in")
    private String direction;

    private Long categoryId;

    private LocalDate date;

    @Size(max = 200, message = "备注最长 200 字")
    private String note;

    /**
     * 撤销删除时带回原来的周期账单；平时手记不传
     */
    private Long recurringId;
}
