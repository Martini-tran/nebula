package com.nebula.manager.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.ai.agent.AgentEngine;
import com.nebula.common.ai.agent.AgentInstanceSnapshot;
import com.nebula.common.ai.agent.AgentInstanceStore;
import com.nebula.common.ai.agent.AgentNodeExecutor;
import com.nebula.common.ai.agent.TransitionRecord;
import com.nebula.common.ai.flow.store.AiAgent;
import com.nebula.common.ai.flow.store.AiAgentInstance;
import com.nebula.common.ai.flow.store.AiAgentInstanceMapper;
import com.nebula.common.ai.flow.store.AiAgentMapper;
import com.nebula.common.ai.flow.store.DatabaseAgentDefinitionRepository;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.AgentInstancePageQuery;
import com.nebula.manager.dto.AgentPageQuery;
import com.nebula.manager.dto.AgentRunRequest;
import com.nebula.manager.dto.AgentSaveRequest;
import com.nebula.manager.dto.AgentSignalRequest;
import com.nebula.manager.vo.AgentDetailVO;
import com.nebula.manager.vo.AgentInstanceVO;
import com.nebula.manager.vo.AgentRunResultVO;
import com.nebula.manager.vo.AgentSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 智能体管理服务实现（管理员端）
 * 定义 CRUD 直接操作 {@link AiAgentMapper}（照 ModelProfile CRUD 风格）；运行/唤醒/续跑走 SDK {@link AgentEngine}；
 * 回放（实例分页 + 转移历史时间线）走 {@link AiAgentInstanceMapper} + {@link AgentInstanceStore}。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class AiAgentAdminServiceImpl implements AiAgentAdminService {

    private final AiAgentMapper agentMapper;

    private final AiAgentInstanceMapper instanceMapper;

    private final AgentEngine agentEngine;

    private final AgentInstanceStore instanceStore;

    private final DatabaseAgentDefinitionRepository agentDefinitionRepository;

    /**
     * 组装运行结果 VO 时需从 attributes 剔除的内部控制键（不回传给前端）
     */
    private static final Set<String> INTERNAL_KEYS = Set.of(
            StateMachineOrchestrator.FAILED_ERROR_KEY,
            StateMachineOrchestrator.SUSPENDED_KEY,
            AgentEngine.CURRENT_INSTANCE_KEY,
            AgentEngine.MAX_AGENT_DEPTH_KEY,
            AgentEngine.SIGNAL_EVENT_KEY,
            AgentNodeExecutor.CALL_STACK_KEY);

    /* ===================== Agent 定义 CRUD ===================== */

    @Override
    public PageResult<AgentSummaryVO> page(AgentPageQuery query) {
        AgentPageQuery safe = query == null ? new AgentPageQuery() : query;
        Page<AiAgent> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<AiAgent>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiAgent::getAgentCode, safe.getKeyword())
                        .or()
                        .like(AiAgent::getName, safe.getKeyword()))
                .eq(safe.getStatus() != null, AiAgent::getStatus, safe.getStatus())
                .orderByDesc(AiAgent::getUpdateTime);
        Page<AiAgent> result = agentMapper.selectPage(page, wrapper);
        List<AgentSummaryVO> rows = result.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AgentDetailVO detail(Long id) {
        AiAgent entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "Agent 不存在: " + id);
        }
        return toDetail(entity);
    }

    @Override
    public Long create(AgentSaveRequest request) {
        validateSave(request);
        AiAgent entity = new AiAgent();
        applySave(entity, request);
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        agentMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AgentSaveRequest request) {
        AiAgent entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "Agent 不存在: " + id);
        }
        // 版本不可变（最小校验）：只有同 code 的最新版本可改，历史版本改动 = 基于最新版本发布新版本
        Integer latest = maxVersionOf(entity.getAgentCode());
        if (latest != null && entity.getVersion() != null && entity.getVersion() < latest) {
            throw new BizException(HttpStatus.BAD_REQUEST,
                    "历史版本不可修改（当前 v" + entity.getVersion() + "，最新 v" + latest + "），请基于最新版本发布新版本");
        }
        validateSave(request);
        // agentCode 是记忆隔离键、version 是版本标识，均不随普通更新改写
        String agentCode = entity.getAgentCode();
        Integer version = entity.getVersion();
        applySave(entity, request);
        entity.setAgentCode(agentCode);
        entity.setVersion(version);
        agentMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        if (agentMapper.selectById(id) == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "Agent 不存在: " + id);
        }
        agentMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        AiAgent entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "Agent 不存在: " + id);
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态非法（0=停用 1=启用）");
        }
        entity.setStatus(status);
        agentMapper.updateById(entity);
    }

    /* ===================== 版本管理 ===================== */

    @Override
    public List<AgentSummaryVO> versions(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "Agent 编码不能为空");
        }
        return agentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                        .eq(AiAgent::getAgentCode, agentCode)
                        .orderByDesc(AiAgent::getVersion))
                .stream().map(this::toSummary).toList();
    }

    @Override
    public Long publishNewVersion(Long id, AgentSaveRequest overrides) {
        AiAgent source = agentMapper.selectById(id);
        if (source == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "Agent 不存在: " + id);
        }
        AiAgent next = new AiAgent();
        next.setAgentCode(source.getAgentCode());
        next.setName(source.getName());
        next.setDescription(source.getDescription());
        next.setFlowCode(source.getFlowCode());
        next.setFlowVersion(source.getFlowVersion());
        next.setInputSchema(source.getInputSchema());
        next.setOutputSchema(source.getOutputSchema());
        next.setMemoryConfig(source.getMemoryConfig());
        next.setDefaultProfileCode(source.getDefaultProfileCode());
        // 用同 code 最大版本 + 1（而非 源.version + 1），支持从历史版本发布且不撞 (agent_code, version) 唯一键
        Integer latest = maxVersionOf(source.getAgentCode());
        next.setVersion((latest == null ? 1 : latest) + 1);
        applyOverrides(next, overrides);
        next.setStatus(1);
        agentMapper.insert(next);
        return next.getId();
    }

    /**
     * 同 agentCode 的最大版本号（无记录返回 null）
     */
    private Integer maxVersionOf(String agentCode) {
        AiAgent top = agentMapper.selectOne(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getAgentCode, agentCode)
                .orderByDesc(AiAgent::getVersion)
                .last("limit 1"));
        return top == null ? null : top.getVersion();
    }

    /**
     * 发布新版本时的可覆盖字段（agentCode/version/status 不可覆盖）
     */
    private void applyOverrides(AiAgent entity, AgentSaveRequest overrides) {
        if (overrides == null) {
            return;
        }
        if (overrides.getName() != null) {
            entity.setName(overrides.getName());
        }
        if (overrides.getDescription() != null) {
            entity.setDescription(overrides.getDescription());
        }
        if (StringUtils.hasText(overrides.getFlowCode())) {
            entity.setFlowCode(overrides.getFlowCode());
        }
        if (overrides.getFlowVersion() != null) {
            entity.setFlowVersion(overrides.getFlowVersion());
        }
        if (overrides.getInputSchema() != null) {
            entity.setInputSchema(overrides.getInputSchema());
        }
        if (overrides.getOutputSchema() != null) {
            entity.setOutputSchema(overrides.getOutputSchema());
        }
        if (overrides.getMemoryConfig() != null) {
            entity.setMemoryConfig(overrides.getMemoryConfig());
        }
        if (overrides.getDefaultProfileCode() != null) {
            entity.setDefaultProfileCode(overrides.getDefaultProfileCode());
        }
    }

    /* ===================== 实例运行 / 唤醒 / 续跑 ===================== */

    @Override
    public AgentRunResultVO run(String agentCode, AgentRunRequest request) {
        if (!StringUtils.hasText(agentCode)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "Agent 编码不能为空");
        }
        AgentRunRequest safe = request == null ? new AgentRunRequest() : request;
        Long userId = UserContext.getUserId();
        try {
            OrchestrationContext ctx = agentEngine.run(agentDefinitionRepository, agentCode,
                    safe.getInputs(), userId == null ? null : String.valueOf(userId), safe.getConversationId());
            return toRunResult(agentCode, ctx);
        } catch (RuntimeException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "运行失败: " + e.getMessage());
        }
    }

    @Override
    public AgentRunResultVO signal(String instanceId, AgentSignalRequest request) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "实例标识不能为空");
        }
        AgentSignalRequest safe = request == null ? new AgentSignalRequest() : request;
        try {
            OrchestrationContext ctx = agentEngine.signal(instanceId, safe.getEvent(), safe.getPayload());
            return toRunResult(null, ctx, instanceId);
        } catch (RuntimeException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "唤醒失败: " + e.getMessage());
        }
    }

    @Override
    public AgentRunResultVO resume(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "实例标识不能为空");
        }
        try {
            OrchestrationContext ctx = agentEngine.resume(instanceId);
            return toRunResult(null, ctx, instanceId);
        } catch (RuntimeException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "续跑失败: " + e.getMessage());
        }
    }

    /* ===================== 实例回放 ===================== */

    @Override
    public PageResult<AgentInstanceVO> instances(AgentInstancePageQuery query) {
        AgentInstancePageQuery safe = query == null ? new AgentInstancePageQuery() : query;
        Page<AiAgentInstance> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiAgentInstance> wrapper = new LambdaQueryWrapper<AiAgentInstance>()
                .eq(StringUtils.hasText(safe.getAgentCode()), AiAgentInstance::getAgentCode, safe.getAgentCode())
                .eq(StringUtils.hasText(safe.getStatus()), AiAgentInstance::getStatus, safe.getStatus())
                .eq(StringUtils.hasText(safe.getUserId()), AiAgentInstance::getUserId, safe.getUserId())
                .orderByDesc(AiAgentInstance::getUpdateTime);
        Page<AiAgentInstance> result = instanceMapper.selectPage(page, wrapper);
        // 列表项不带转移时间线（详情才带），只回实例头
        List<AgentInstanceVO> rows = result.getRecords().stream().map(this::toInstanceHead).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AgentInstanceVO instanceDetail(String instanceId) {
        AgentInstanceSnapshot snapshot = instanceStore.load(instanceId);
        if (snapshot == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "实例不存在: " + instanceId);
        }
        AgentInstanceVO vo = new AgentInstanceVO();
        vo.setInstanceId(snapshot.instanceId());
        vo.setAgentCode(snapshot.agentCode());
        vo.setFlowCode(snapshot.flowCode());
        vo.setAgentVersion(snapshot.agentVersion());
        vo.setFlowVersion(snapshot.flowVersion());
        // 版本锁定的源图定义（含画布坐标），前端回放画布直接从它建图，不回查 node/edge
        vo.setGraphSnapshot(snapshot.graphSnapshot());
        vo.setStatus(snapshot.status());
        vo.setCurrentState(snapshot.currentState());
        AiAgentInstance entity = instanceMapper.selectOne(new LambdaQueryWrapper<AiAgentInstance>()
                .eq(AiAgentInstance::getInstanceId, instanceId)
                .last("limit 1"));
        if (entity != null) {
            vo.setErrorMsg(entity.getErrorMsg());
        }
        if (snapshot.awaitingEvents() != null) {
            vo.getAwaitingEvents().addAll(snapshot.awaitingEvents());
        }
        vo.setTransitionCount(snapshot.transitionCount());
        if (snapshot.contextSnapshot() != null) {
            snapshot.contextSnapshot().forEach((k, v) -> {
                if (!INTERNAL_KEYS.contains(k)) {
                    vo.getContextSnapshot().put(k, v);
                }
            });
        }
        // 转移历史时间线（按 seq 升序，含 RETRY 行）
        for (TransitionRecord t : instanceStore.loadTransitions(instanceId)) {
            AgentInstanceVO.AgentTransitionVO tv = new AgentInstanceVO.AgentTransitionVO();
            tv.setSeq(t.seq());
            tv.setFromState(t.fromState());
            tv.setToState(t.toState());
            tv.setEventName(t.eventName());
            tv.setAttempt(t.attempt());
            tv.setOutcome(t.outcome());
            if (t.nodeResult() != null) {
                tv.getNodeResult().putAll(t.nodeResult());
            }
            vo.getTransitions().add(tv);
        }
        return vo;
    }

    /* ===================== 内部：转换 ===================== */

    private AgentRunResultVO toRunResult(String agentCode, OrchestrationContext ctx) {
        String instanceId = ctx.getString(AgentEngine.CURRENT_INSTANCE_KEY);
        return toRunResult(agentCode, ctx, instanceId);
    }

    /**
     * 组装运行结果：实例状态从 store 重新 load 取准确值（run/signal/resume 后实例已落库），attributes 剔除内部键。
     */
    private AgentRunResultVO toRunResult(String agentCode, OrchestrationContext ctx, String instanceId) {
        AgentRunResultVO vo = new AgentRunResultVO();
        vo.setInstanceId(instanceId);
        vo.setAgentCode(agentCode);
        if (ctx != null) {
            ctx.attributes().forEach((k, v) -> {
                if (!INTERNAL_KEYS.contains(k)) {
                    vo.getAttributes().put(k, v);
                }
            });
        }
        // 从落库实例取准确状态（含挂起 awaiting_events）
        if (instanceId != null && instanceStore != null) {
            AgentInstanceSnapshot snapshot = instanceStore.load(instanceId);
            if (snapshot != null) {
                vo.setStatus(snapshot.status());
                vo.setCurrentState(snapshot.currentState());
                if (vo.getAgentCode() == null) {
                    vo.setAgentCode(snapshot.agentCode());
                }
                if (snapshot.awaitingEvents() != null) {
                    vo.getAwaitingEvents().addAll(snapshot.awaitingEvents());
                }
            }
        }
        return vo;
    }

    private AgentInstanceVO toInstanceHead(AiAgentInstance entity) {
        AgentInstanceVO vo = new AgentInstanceVO();
        vo.setInstanceId(entity.getInstanceId());
        vo.setAgentCode(entity.getAgentCode());
        vo.setFlowCode(entity.getFlowCode());
        vo.setStatus(entity.getStatus());
        vo.setCurrentState(entity.getCurrentState());
        vo.setTransitionCount(entity.getTransitionCount());
        return vo;
    }

    private AgentDetailVO toDetail(AiAgent entity) {
        AgentDetailVO vo = new AgentDetailVO();
        vo.setId(entity.getId());
        vo.setAgentCode(entity.getAgentCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setFlowCode(entity.getFlowCode());
        vo.setFlowVersion(entity.getFlowVersion());
        vo.setInputSchema(entity.getInputSchema());
        vo.setOutputSchema(entity.getOutputSchema());
        vo.setMemoryConfig(entity.getMemoryConfig());
        vo.setDefaultProfileCode(entity.getDefaultProfileCode());
        vo.setVersion(entity.getVersion());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private AgentSummaryVO toSummary(AiAgent entity) {
        AgentSummaryVO vo = new AgentSummaryVO();
        vo.setId(entity.getId());
        vo.setAgentCode(entity.getAgentCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setFlowCode(entity.getFlowCode());
        vo.setFlowVersion(entity.getFlowVersion());
        vo.setDefaultProfileCode(entity.getDefaultProfileCode());
        vo.setVersion(entity.getVersion());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private void applySave(AiAgent entity, AgentSaveRequest request) {
        entity.setAgentCode(request.getAgentCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setFlowCode(request.getFlowCode());
        entity.setFlowVersion(request.getFlowVersion() == null ? 1 : request.getFlowVersion());
        entity.setInputSchema(request.getInputSchema());
        entity.setOutputSchema(request.getOutputSchema());
        entity.setMemoryConfig(request.getMemoryConfig());
        entity.setDefaultProfileCode(request.getDefaultProfileCode());
        if (request.getVersion() != null) {
            entity.setVersion(request.getVersion());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
    }

    private void validateSave(AgentSaveRequest request) {
        if (request == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getAgentCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "Agent 编码不能为空");
        }
        if (!StringUtils.hasText(request.getFlowCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "引用的流程编码不能为空");
        }
    }
}
