package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 任务侧栏计数（只算未完成）
 *
 * <p>计数用 int：全局把 Long 序列化成字符串，"0" 在前端是真值，徽标会显示 0。</p>
 */
@Data
public class TaskStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int inbox;

    /**
     * 今天及过期
     */
    private int today;

    private int overdue;

    /**
     * 未来 7 天（含今天）
     */
    private int plan;

    /**
     * 各清单未完成数，键为清单ID
     */
    private Map<String, Integer> lists;
}
