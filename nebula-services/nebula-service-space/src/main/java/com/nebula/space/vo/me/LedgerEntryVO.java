package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账流水视图对象
 *
 * <p>金额用 BigDecimal 输出成数字：全局把 Long 序列化成字符串（防雪花 ID 丢精度），金额要参与前端加减。</p>
 */
@Data
public class LedgerEntryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 分
     */
    private BigDecimal amount;

    private String direction;

    private Long categoryId;

    private LocalDate date;

    private String note;

    private Long recurringId;

    private LocalDateTime createTime;
}
