package com.nebula.space.dto.me;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 保存某月预算（整份覆盖），之后的月份没设时沿用
 */
@Data
public class LedgerBudgetSaveRequest {

    /**
     * 总预算（分）；null 为各分类之和
     */
    @Min(0)
    @Max(99_999_999_999L)
    private Long total;

    /**
     * 分类ID → 额度（分）
     */
    @Size(max = 200)
    private Map<String, Long> items;
}
