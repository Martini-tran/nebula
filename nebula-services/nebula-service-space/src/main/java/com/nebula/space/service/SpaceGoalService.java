package com.nebula.space.service;

import com.nebula.space.dto.me.GoalSaveRequest;
import com.nebula.space.vo.me.GoalVO;

import java.util.List;

/**
 * 年度目标服务：只操作当前登录用户自己的目标
 *
 * <p>只存定义；进度订阅习惯、稍后读、记账、任务清单的数据由前端实时算（views/goals/goalProgress.ts），不落库。</p>
 */
public interface SpaceGoalService {

    /**
     * 某一年的目标，按排序号
     */
    List<GoalVO> list(Integer year);

    /**
     * 所有年份的目标，按年份、排序号（导出用）
     */
    List<GoalVO> listAll();

    GoalVO create(GoalSaveRequest req);

    GoalVO update(Long id, GoalSaveRequest req);

    void delete(Long id);
}
