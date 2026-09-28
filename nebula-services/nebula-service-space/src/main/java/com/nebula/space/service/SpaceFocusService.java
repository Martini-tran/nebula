package com.nebula.space.service;

import com.nebula.space.dto.me.FocusSessionCreateRequest;
import com.nebula.space.dto.me.FocusSessionQuery;
import com.nebula.space.vo.me.FocusSessionVO;

import java.util.List;

/**
 * 专注记录服务：只操作当前登录用户自己的记录
 *
 * <p>记录只增不改，统计（本周分钟数、任务累计）由前端按列表计算。</p>
 */
public interface SpaceFocusService {

    List<FocusSessionVO> list(FocusSessionQuery query);

    FocusSessionVO create(FocusSessionCreateRequest req);
}
