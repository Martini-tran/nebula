package com.nebula.space.service;

import com.nebula.space.dto.me.LedgerRecurringSaveRequest;
import com.nebula.space.vo.me.LedgerRecurringVO;

import java.util.List;

/**
 * 周期账单服务：房租、订阅这类每月固定的，到日子自动记一笔
 *
 * <p>不用定时任务：取流水前调 {@link #fill(Long)} 把到今天为止该记的补上。每个周期账单记着「已生成到哪个月」，
 * 只往后补——删掉的那笔不会再补回来；暂停期间的月份在恢复时跳过。</p>
 */
public interface SpaceLedgerRecurringService {

    List<LedgerRecurringVO> list();

    /**
     * 新建后立即补一次：本月那天已经过了的话，这笔马上就有
     */
    LedgerRecurringVO create(LedgerRecurringSaveRequest req);

    LedgerRecurringVO update(Long id, LedgerRecurringSaveRequest req);

    /**
     * 删除周期账单，已经记下的流水保留
     */
    void delete(Long id);

    /**
     * 把某个用户启用中的周期账单补到今天
     */
    void fill(Long userId);
}
