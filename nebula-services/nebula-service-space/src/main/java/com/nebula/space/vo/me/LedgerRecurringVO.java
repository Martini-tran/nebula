package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 周期账单视图对象（金额用 BigDecimal 输出成数字，理由同 LedgerEntryVO）
 */
@Data
public class LedgerRecurringVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String note;

    /**
     * 分
     */
    private BigDecimal amount;

    private String direction;

    private Long categoryId;

    private Integer day;

    private Boolean active;

    private String startMonth;
}
