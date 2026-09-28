package com.nebula.space.service;

import com.nebula.space.dto.me.HabitLogQuery;
import com.nebula.space.dto.me.HabitLogSetRequest;
import com.nebula.space.dto.me.HabitSaveRequest;
import com.nebula.space.vo.me.HabitLogVO;
import com.nebula.space.vo.me.HabitVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 习惯与打卡服务：只操作当前登录用户自己的数据
 *
 * <p>连续天数、完成率按频率规则由前端实时计算，不落库。</p>
 */
public interface SpaceHabitService {

    List<HabitVO> list(boolean includeArchived);

    HabitVO create(HabitSaveRequest req);

    HabitVO update(Long id, HabitSaveRequest req);

    /**
     * 删除习惯，连同它的打卡记录
     */
    void delete(Long id);

    List<HabitLogVO> logs(HabitLogQuery query);

    /**
     * 写入某天的值（覆盖）；value = 0 删除那天的打卡并返回 null
     */
    HabitLogVO setLog(Long habitId, LocalDate date, HabitLogSetRequest req);
}
