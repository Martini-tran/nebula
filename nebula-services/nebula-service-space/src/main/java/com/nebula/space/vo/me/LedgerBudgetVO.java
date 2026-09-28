package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 某月预算视图对象；这个月没设时是沿用的那个月的值，month 仍是查询的月份（金额用 BigDecimal 输出成数字）
 */
@Data
public class LedgerBudgetVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String month;

    /**
     * 总预算（分）；null 为各分类之和
     */
    private BigDecimal total;

    /**
     * 分类ID → 额度（分）
     */
    private Map<String, BigDecimal> items;
}
