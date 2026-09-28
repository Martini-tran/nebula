package com.nebula.space.service;

import com.nebula.space.dto.me.TaskListSaveRequest;
import com.nebula.space.vo.me.TaskListVO;

import java.util.List;

/**
 * 任务清单服务：只操作当前登录用户自己的清单
 */
public interface SpaceTaskListService {

    List<TaskListVO> list();

    TaskListVO create(TaskListSaveRequest req);

    void update(Long id, TaskListSaveRequest req);

    /**
     * 删除清单，其中的任务回到「无清单」
     */
    void delete(Long id);

    /**
     * 校验清单属于当前用户，不属于则报 404
     */
    void requireOwned(Long id, Long userId);
}
