package com.nebula.space.dto.me;

import lombok.Data;

/**
 * 任务列表查询
 */
@Data
public class TaskQuery {

    /**
     * 视图：inbox 收件箱 / today 今天（含过期）/ plan 未来 7 天 / done 已完成 / all（默认）
     */
    private String view;

    private Long listId;

    private String sourceType;

    private String sourceId;
}
