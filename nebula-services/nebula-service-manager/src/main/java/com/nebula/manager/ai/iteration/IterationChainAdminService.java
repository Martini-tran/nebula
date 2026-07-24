package com.nebula.manager.ai;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.IterationChainPageQuery;
import com.nebula.manager.dto.IterationChainSaveRequest;
import com.nebula.manager.vo.IterationChainVO;

/**
 * 跨实例迭代链管理服务（管理员端）
 * 链的 CRUD + 暂停/恢复 + 立即推进一轮（run-now）+ 详情（含已产出实例时间线）。定义类操作直接走
 * {@code AiAgentIterationMapper}（照 {@link AiAgentAdminService} 风格）；推进由 {@code IterationDriver} 定时负责，
 * run-now 只是把 next_run_at 置当前并触发一次 tick。见 docs/跨实例迭代层设计.md 第七章。
 *
 * @author nebula
 */
public interface IterationChainAdminService {

    /**
     * 分页查询迭代链
     */
    PageResult<IterationChainVO> page(IterationChainPageQuery query);

    /**
     * 链详情（含已产出实例时间线，回溯 last_instance_id 链）
     */
    IterationChainVO detail(String chainId);

    /**
     * 创建迭代链（= 开一个"系列"），返回 chainId
     */
    String create(IterationChainSaveRequest request);

    /**
     * 更新迭代链配置（ACTIVE 的链也可改 cron/carryOver/until 等，下轮生效）
     */
    void update(String chainId, IterationChainSaveRequest request);

    /**
     * 暂停一条链（ACTIVE → PAUSED，定时器不再扫它）
     */
    void pause(String chainId);

    /**
     * 恢复一条链（PAUSED → ACTIVE，清零连续失败计数）
     */
    void resume(String chainId);

    /**
     * 立即推进一轮（run-now）：把 next_run_at 置当前，下次扫描即跑（仍走 Redis 锁，与定时同一条路径）
     */
    void runNow(String chainId);

    /**
     * 删除链（不删已产出实例）
     */
    void delete(String chainId);
}
