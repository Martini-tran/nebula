package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 任务侧栏计数（只算未完成）
 */
@Data
public class TaskStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long inbox;

    /**
     * 今天及过期
     */
    private long today;

    private long overdue;

    /**
     * 未来 7 天（含今天）
     */
    private long plan;

    /**
     * 各清单未完成数，键为清单ID
     */
    private Map<String, Long> lists;
}
