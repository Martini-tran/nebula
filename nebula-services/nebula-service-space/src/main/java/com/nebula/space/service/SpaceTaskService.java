package com.nebula.space.service;

import com.nebula.space.dto.me.TaskQuery;
import com.nebula.space.dto.me.TaskSaveRequest;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.vo.me.TaskCompleteVO;
import com.nebula.space.vo.me.TaskStatsVO;
import com.nebula.space.vo.me.TaskVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务服务：只操作当前登录用户自己的任务
 */
public interface SpaceTaskService {

    List<TaskVO> list(TaskQuery query);

    /**
     * 全局搜索召回：标题 / 备注 / 子任务命中，#清单名、@来源、is: 状态、日期范围按库能筛的先筛，最近创建的在前
     */
    List<TaskVO> search(SearchCriteria q, int limit);

    /**
     * 按天看的任务：截止日在 from ~ to 之间的，加上 today 之前过期没做完的；doneSince 不为空时再加上这之后完成的
     */
    List<TaskVO> listForDays(LocalDate from, LocalDate to, LocalDate today, LocalDateTime doneSince);

    /**
     * 所有没做完的，加上 doneSince 之后完成的（周回顾用）
     */
    List<TaskVO> listOpenOrDoneSince(LocalDateTime doneSince);

    TaskVO detail(Long id);

    TaskStatsVO stats();

    TaskVO create(TaskSaveRequest req);

    TaskVO update(Long id, TaskSaveRequest req);

    /**
     * 完成 / 撤销完成；重复任务完成时生成下一次
     */
    TaskCompleteVO complete(Long id, boolean done);

    void delete(Long id);
}
