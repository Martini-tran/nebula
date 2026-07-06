package com.nebula.manager.ai;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.AgentInstancePageQuery;
import com.nebula.manager.dto.AgentPageQuery;
import com.nebula.manager.dto.AgentRunRequest;
import com.nebula.manager.dto.AgentSaveRequest;
import com.nebula.manager.dto.AgentSignalRequest;
import com.nebula.manager.vo.AgentDetailVO;
import com.nebula.manager.vo.AgentInstanceVO;
import com.nebula.manager.vo.AgentRunResultVO;
import com.nebula.manager.vo.AgentSummaryVO;

/**
 * AI 智能体管理服务（管理员端）
 * Agent 定义 CRUD（{@code ai_agent}）+ 实例运行/唤醒/续跑（状态机实例）+ 实例回放（转移历史）。
 * 定义 CRUD 直接操作 {@code AiAgentMapper}；运行类走 SDK {@code AgentEngine}；回放走 {@code AgentInstanceStore}。
 *
 * @author nebula
 */
public interface AiAgentAdminService {

    /* ---- Agent 定义 CRUD ---- */

    /**
     * 分页查询 Agent 定义
     */
    PageResult<AgentSummaryVO> page(AgentPageQuery query);

    /**
     * 获取 Agent 定义详情（含 IO 契约 / 记忆配置全字段，用于编辑回显）
     */
    AgentDetailVO detail(Long id);

    /**
     * 创建 Agent 定义，返回主键ID
     */
    Long create(AgentSaveRequest request);

    /**
     * 更新 Agent 定义
     */
    void update(Long id, AgentSaveRequest request);

    /**
     * 删除 Agent 定义
     */
    void delete(Long id);

    /**
     * 更新启用/停用状态
     */
    void updateStatus(Long id, Integer status);

    /* ---- 实例运行 / 唤醒 / 续跑 ---- */

    /**
     * 按 agentCode 创建实例并执行
     */
    AgentRunResultVO run(String agentCode, AgentRunRequest request);

    /**
     * 唤醒一个 SUSPENDED 实例（Human-in-the-loop）
     */
    AgentRunResultVO signal(String instanceId, AgentSignalRequest request);

    /**
     * 崩溃恢复：从落库状态恢复一个实例并续跑（RUNNING 断点）
     */
    AgentRunResultVO resume(String instanceId);

    /* ---- 实例回放 ---- */

    /**
     * 分页查询实例
     */
    PageResult<AgentInstanceVO> instances(AgentInstancePageQuery query);

    /**
     * 实例详情（含转移历史时间线，用于回放）
     */
    AgentInstanceVO instanceDetail(String instanceId);
}
