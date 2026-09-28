package com.nebula.space.service;

import com.nebula.space.dto.me.AnniversarySaveRequest;
import com.nebula.space.vo.me.AnniversaryVO;

import java.util.List;

/**
 * 纪念日服务：只操作当前登录用户自己的纪念日
 *
 * <p>下一次在哪天、农历换算、到期前生成任务都在前端（views/goals/annivDates.ts），这里只存定义和 taskFor 标记。</p>
 */
public interface SpaceAnniversaryService {

    List<AnniversaryVO> list();

    AnniversaryVO create(AnniversarySaveRequest req);

    AnniversaryVO update(Long id, AnniversarySaveRequest req);

    void delete(Long id);
}
