package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.ai.agent.AgentDefinition;
import com.nebula.common.ai.agent.AgentInstanceSnapshot;
import com.nebula.common.ai.agent.AgentInstanceStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

/**
 * 数据库版 Agent 实例存储
 * 以 MyBatis-Plus 把状态机实例落 {@code ai_agent_instance}（实例头）与 {@code ai_agent_instance_transition}
 * （转移轨迹）两表，支撑回放 / 续跑。实现 SDK 的 {@link AgentInstanceStore}，与 {@link DatabaseRunStateStore} 同构：
 * 由 {@link AiAgentStoreAutoConfiguration} 以 {@code @ConditionalOnMissingBean} 装配，装配后 {@code AgentEngine}
 * 获得实例持久化能力（缺失时退化为纯内存执行）。
 *
 * <p>JSON 列（graphSnapshot 已由门面序列化好直接落；inputs/contextSnapshot/nodeResult 经 {@link FlowJsonCodec}）。
 * instanceId 用 UUID 生成。<b>分级落盘</b>：普通转移只 append 轻量 transition，context_snapshot 仅终态刷新。
 *
 * @author nebula
 */
@Slf4j
public class DatabaseAgentInstanceStore implements AgentInstanceStore {

    private final AiAgentInstanceMapper instanceMapper;

    private final AiAgentInstanceTransitionMapper transitionMapper;

    public DatabaseAgentInstanceStore(AiAgentInstanceMapper instanceMapper,
                                      AiAgentInstanceTransitionMapper transitionMapper) {
        this.instanceMapper = instanceMapper;
        this.transitionMapper = transitionMapper;
    }

    @Override
    public String create(AgentDefinition definition, String graphSnapshot,
                         String userId, String conversationId, Map<String, Object> inputs) {
        String instanceId = definition.getAgentCode() + "-" + UUID.randomUUID().toString().replace("-", "");
        AiAgentInstance inst = new AiAgentInstance();
        inst.setInstanceId(instanceId);
        inst.setAgentCode(definition.getAgentCode());
        inst.setAgentVersion(definition.getVersion());
        inst.setFlowCode(definition.getFlowCode());
        inst.setFlowVersion(definition.getFlowVersion());
        inst.setGraphSnapshot(graphSnapshot);
        inst.setUserId(userId);
        inst.setConversationId(conversationId);
        inst.setStatus("RUNNING");
        inst.setInputs(FlowJsonCodec.write(inputs));
        inst.setContextSnapshotSeq(-1);
        inst.setTransitionCount(0);
        inst.setLockVersion(0);
        instanceMapper.insert(inst);
        return instanceId;
    }

    @Override
    public AgentInstanceSnapshot load(String instanceId) {
        AiAgentInstance inst = findByInstanceId(instanceId);
        if (inst == null) {
            return null;
        }
        return new AgentInstanceSnapshot(
                inst.getInstanceId(), inst.getAgentCode(),
                inst.getAgentVersion() == null ? 1 : inst.getAgentVersion(),
                inst.getFlowCode(), inst.getFlowVersion() == null ? 1 : inst.getFlowVersion(),
                inst.getGraphSnapshot(), inst.getUserId(), inst.getConversationId(),
                inst.getStatus(), inst.getCurrentState(),
                FlowJsonCodec.readObjectMap(inst.getInputs()),
                FlowJsonCodec.readObjectMap(inst.getContextSnapshot()),
                inst.getContextSnapshotSeq() == null ? -1 : inst.getContextSnapshotSeq(),
                inst.getTransitionCount() == null ? 0 : inst.getTransitionCount());
    }

    @Override
    public void appendSuccess(String instanceId, String stateCode, int seq, int attempt,
                              Map<String, Object> contextDelta) {
        AiAgentInstance inst = findByInstanceId(instanceId);
        if (inst == null) {
            log.warn("落 SUCCESS 轨迹找不到实例[{}]", instanceId);
            return;
        }
        // 轻量更新 current_state，不刷 context_snapshot（分级落盘）
        inst.setCurrentState(stateCode);
        instanceMapper.updateById(inst);
        insertTransition(instanceId, seq, null, stateCode, attempt, "SUCCESS",
                FlowJsonCodec.write(contextDelta));
    }

    @Override
    public void appendRetry(String instanceId, String stateCode, int seq, int attempt, String errorSummary) {
        insertTransition(instanceId, seq, null, stateCode, attempt, "RETRY", errorSummary);
    }

    @Override
    public void appendTransition(String instanceId, String fromState, String toState, int seq) {
        AiAgentInstance inst = findByInstanceId(instanceId);
        if (inst == null) {
            return;
        }
        inst.setCurrentState(toState);
        inst.setTransitionCount((inst.getTransitionCount() == null ? 0 : inst.getTransitionCount()) + 1);
        instanceMapper.updateById(inst);
    }

    @Override
    public void markTerminal(String instanceId, String terminalState, Map<String, Object> contextSnapshot) {
        AiAgentInstance inst = findByInstanceId(instanceId);
        if (inst == null) {
            return;
        }
        inst.setStatus("SUCCESS");
        inst.setCurrentState(terminalState);
        refreshSnapshot(inst, contextSnapshot);
        instanceMapper.updateById(inst);
    }

    @Override
    public void markFailed(String instanceId, String failedState, String error, Map<String, Object> contextSnapshot) {
        AiAgentInstance inst = findByInstanceId(instanceId);
        if (inst == null) {
            return;
        }
        inst.setStatus("FAILED");
        inst.setErrorMsg(error);
        refreshSnapshot(inst, contextSnapshot);
        instanceMapper.updateById(inst);
    }

    /* ===================== 内部 ===================== */

    private void refreshSnapshot(AiAgentInstance inst, Map<String, Object> contextSnapshot) {
        inst.setContextSnapshot(FlowJsonCodec.write(contextSnapshot));
        // context_snapshot_seq = 全量刷新时刻最大 transition seq（含 RETRY 行），恢复时从此 seq 之后重放增量
        inst.setContextSnapshotSeq(maxTransitionSeq(inst.getInstanceId()));
    }

    private Integer maxTransitionSeq(String instanceId) {
        AiAgentInstanceTransition last = transitionMapper.selectOne(
                new LambdaQueryWrapper<AiAgentInstanceTransition>()
                        .eq(AiAgentInstanceTransition::getInstanceId, instanceId)
                        .orderByDesc(AiAgentInstanceTransition::getSeq)
                        .last("limit 1"));
        return last == null || last.getSeq() == null ? -1 : last.getSeq();
    }

    private void insertTransition(String instanceId, int seq, String fromState, String toState,
                                  int attempt, String outcome, String nodeResult) {
        AiAgentInstanceTransition t = new AiAgentInstanceTransition();
        t.setInstanceId(instanceId);
        t.setSeq(seq);
        t.setFromState(fromState);
        t.setToState(toState);
        t.setAttempt(attempt);
        t.setOutcome(outcome);
        t.setIsCompensation(0);
        t.setNodeResult(nodeResult);
        transitionMapper.insert(t);
    }

    private AiAgentInstance findByInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        return instanceMapper.selectOne(new LambdaQueryWrapper<AiAgentInstance>()
                .eq(AiAgentInstance::getInstanceId, instanceId)
                .last("limit 1"));
    }
}
