package com.nebula.common.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Agent 实例存储
 * SDK 默认实现，无存储依赖：把实例头与转移轨迹存内存 Map，供无数据库场景与单元测试验证「创建→落轨迹→终态」闭环。
 * 数据库版由 {@code nebula-sdk-ai-flow} 的 {@code DatabaseAgentInstanceStore} 提供并覆盖。
 *
 * <p>本实现严格保持与接口一致的落库时序，便于单测断言 transition 的 seq/outcome/delta 与 context_snapshot 分级刷新。
 *
 * @author nebula
 */
public class InMemoryAgentInstanceStore implements AgentInstanceStore {

    /**
     * 一条转移轨迹（对应 ai_agent_instance_transition 一行）
     */
    public record Transition(int seq, String fromState, String toState, int attempt,
                             String outcome, Object nodeResult) {
    }

    /**
     * 一个实例的内存态（对应 ai_agent_instance 一行 + 其转移列表）
     */
    public static final class Instance {
        String instanceId;
        String agentCode;
        int agentVersion;
        String flowCode;
        int flowVersion;
        String graphSnapshot;
        String userId;
        String conversationId;
        String status;
        String currentState;
        Map<String, Object> inputs;
        Map<String, Object> contextSnapshot = new LinkedHashMap<>();
        int contextSnapshotSeq = -1;
        int transitionCount;
        String errorMsg;
        final List<Transition> transitions = new ArrayList<>();

        public String status() {
            return status;
        }

        public String currentState() {
            return currentState;
        }

        public int transitionCount() {
            return transitionCount;
        }

        public Map<String, Object> contextSnapshot() {
            return contextSnapshot;
        }

        public int contextSnapshotSeq() {
            return contextSnapshotSeq;
        }

        public String errorMsg() {
            return errorMsg;
        }

        public List<Transition> transitions() {
            return transitions;
        }
    }

    private final Map<String, Instance> instances = new ConcurrentHashMap<>();

    @Override
    public String create(AgentDefinition definition, String graphSnapshot,
                         String userId, String conversationId, Map<String, Object> inputs) {
        String instanceId = definition.getAgentCode() + "-" + UUID.randomUUID().toString().replace("-", "");
        Instance inst = new Instance();
        inst.instanceId = instanceId;
        inst.agentCode = definition.getAgentCode();
        inst.agentVersion = definition.getVersion();
        inst.flowCode = definition.getFlowCode();
        inst.flowVersion = definition.getFlowVersion();
        inst.graphSnapshot = graphSnapshot;
        inst.userId = userId;
        inst.conversationId = conversationId;
        inst.status = "RUNNING";
        inst.inputs = inputs == null ? Map.of() : new LinkedHashMap<>(inputs);
        instances.put(instanceId, inst);
        return instanceId;
    }

    @Override
    public AgentInstanceSnapshot load(String instanceId) {
        Instance inst = instances.get(instanceId);
        if (inst == null) {
            return null;
        }
        return new AgentInstanceSnapshot(inst.instanceId, inst.agentCode, inst.agentVersion,
                inst.flowCode, inst.flowVersion, inst.graphSnapshot, inst.userId, inst.conversationId,
                inst.status, inst.currentState, new LinkedHashMap<>(inst.inputs),
                new LinkedHashMap<>(inst.contextSnapshot), inst.contextSnapshotSeq, inst.transitionCount);
    }

    @Override
    public void appendSuccess(String instanceId, String stateCode, int seq, int attempt,
                              Map<String, Object> contextDelta) {
        Instance inst = instances.get(instanceId);
        if (inst == null) {
            return;
        }
        inst.currentState = stateCode;
        inst.transitions.add(new Transition(seq, null, stateCode, attempt, "SUCCESS",
                contextDelta == null ? Map.of() : new LinkedHashMap<>(contextDelta)));
    }

    @Override
    public void appendRetry(String instanceId, String stateCode, int seq, int attempt, String errorSummary) {
        Instance inst = instances.get(instanceId);
        if (inst == null) {
            return;
        }
        inst.transitions.add(new Transition(seq, null, stateCode, attempt, "RETRY", errorSummary));
    }

    @Override
    public void appendTransition(String instanceId, String fromState, String toState, int seq) {
        Instance inst = instances.get(instanceId);
        if (inst == null) {
            return;
        }
        inst.currentState = toState;
        inst.transitionCount++;
    }

    @Override
    public void markTerminal(String instanceId, String terminalState, Map<String, Object> contextSnapshot) {
        Instance inst = instances.get(instanceId);
        if (inst == null) {
            return;
        }
        inst.status = "SUCCESS";
        inst.currentState = terminalState;
        refreshSnapshot(inst, contextSnapshot);
    }

    @Override
    public void markFailed(String instanceId, String failedState, String error, Map<String, Object> contextSnapshot) {
        Instance inst = instances.get(instanceId);
        if (inst == null) {
            return;
        }
        inst.status = "FAILED";
        inst.errorMsg = error;
        refreshSnapshot(inst, contextSnapshot);
    }

    private void refreshSnapshot(Instance inst, Map<String, Object> contextSnapshot) {
        inst.contextSnapshot = contextSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(contextSnapshot);
        // 记 context_snapshot_seq = 全量刷新时刻最大 transition seq（含 RETRY 行）
        inst.contextSnapshotSeq = inst.transitions.stream().mapToInt(Transition::seq).max().orElse(-1);
    }

    /**
     * 读取实例内存态（测试断言用）
     *
     * @param instanceId 实例标识
     * @return 实例内存态，不存在返回 null
     */
    public Instance peek(String instanceId) {
        return instances.get(instanceId);
    }

    /**
     * 全部实例标识（测试断言用）
     *
     * @return 实例标识列表
     */
    public List<String> instanceCodes() {
        return new ArrayList<>(instances.keySet());
    }
}
