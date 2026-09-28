package com.nebula.space.service;

import com.nebula.space.dto.me.TaskQuery;
import com.nebula.space.dto.me.TaskSaveRequest;
import com.nebula.space.vo.me.TaskCompleteVO;
import com.nebula.space.vo.me.TaskStatsVO;
import com.nebula.space.vo.me.TaskVO;

import java.util.List;

/**
 * 任务服务：只操作当前登录用户自己的任务
 */
public interface SpaceTaskService {

    List<TaskVO> list(TaskQuery query);

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
