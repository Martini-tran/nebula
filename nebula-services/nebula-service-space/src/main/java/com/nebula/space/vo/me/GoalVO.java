package com.nebula.space.vo.me;

import com.nebula.space.dto.me.GoalKeyResult;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 年度目标视图对象：只有定义，进度由前端按来源实时算
 */
@Data
public class GoalVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Integer year;

    private String title;

    private String icon;

    private String kind;

    private BigDecimal target;

    private String unit;

    private String source;

    private Long sourceId;

    private BigDecimal factor;

    private BigDecimal baseline;

    private BigDecimal manualValue;

    private List<GoalKeyResult> krs;

    private Integer sortOrder;

    private LocalDateTime createTime;
}
