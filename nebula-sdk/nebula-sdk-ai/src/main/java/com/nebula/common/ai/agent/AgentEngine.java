package com.nebula.common.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowDefinitionRepository;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.memory.AgentMemory;
import com.nebula.common.ai.memory.AgentMemoryRegistry;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.statemachine.StateMachineGraph;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import com.nebula.common.ai.orchestration.statemachine.TransitionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行门面（阶段 2）
 * 收口文档三·执行流程：<b>创建实例 → Import Memory → 执行状态机 → Export Memory → 落终态</b>。
 * 是对 {@link StateMachineOrchestrator} 纯内核的一层封装，补齐持久化（{@link AgentInstanceStore}）与长期记忆
 * 首尾钩子（{@link AgentMemory}），支撑 1 Flow : N Agent 复用（不同 {@link AgentDefinition} 引用同一 flowCode）。
 *
 * <p><b>版本锁定</b>：创建实例时把编译源图（{@link FlowDefinition}）序列化为 graph_snapshot 落库，续跑/回放只读它。
 * <p><b>记忆铁律</b>：执行过程中节点只写 {@link OrchestrationContext}，绝不直写 Memory；记忆的"读"在创建时 Import
 * 定格、"写"在结束时 Export 落盘。<b>Import 先写、Inputs 后写，同名键以 Inputs 为准</b>。
 * <p><b>Export 不翻盘</b>：实例业务已跑完（终态 SUCCESS），Export 写记忆失败只记 warning，不把已成功的业务判失败。
 *
 * @author nebula
 */
public class AgentEngine {

    private static final Logger log = LoggerFactory.getLogger(AgentEngine.class);

    /**
     * signal 唤醒时把外部事件名写入 context 的约定键，供出边 guard（SpEL）裁决去向（阶段 3）
     */
    public static final String SIGNAL_EVENT_KEY = "__signalEvent";

    /**
     * 执行期把当前实例 instanceId 写入 context 的约定键，供 {@link AgentNodeExecutor} 落子实例的
     * parent_instance_id（递归子 Agent，阶段 3）
     */
    public static final String CURRENT_INSTANCE_KEY = "__currentInstanceId";

    /**
     * 执行期把本流程的递归深度上限（flow.maxAgentDepth）写入 context 的约定键，供 {@link AgentNodeExecutor}
     * 做深度治理（递归子 Agent，阶段 3）
     */
    public static final String MAX_AGENT_DEPTH_KEY = "__maxAgentDepth";

    private final FlowDefinitionRepository flowRepository;

    private final FlowStateMachineFactory stateMachineFactory;

    private final StateMachineOrchestrator orchestrator;

    private final AgentInstanceStore instanceStore;

    private final AgentMemoryRegistry memoryRegistry;

    private final ObjectMapper objectMapper;

    /**
     * Agent 定义仓储：signal 唤醒续跑时按 agentCode 取回定义拿 memory_config 做 Export（阶段 3）。可空。
     */
    private final AgentDefinitionRepository definitionRepository;

    public AgentEngine(FlowDefinitionRepository flowRepository,
                       FlowStateMachineFactory stateMachineFactory,
                       StateMachineOrchestrator orchestrator,
                       AgentInstanceStore instanceStore,
                       AgentMemoryRegistry memoryRegistry,
                       ObjectMapper objectMapper) {
        this(flowRepository, stateMachineFactory, orchestrator, instanceStore,
                memoryRegistry, objectMapper, null);
    }

    public AgentEngine(FlowDefinitionRepository flowRepository,
                       FlowStateMachineFactory stateMachineFactory,
                       StateMachineOrchestrator orchestrator,
                       AgentInstanceStore instanceStore,
                       AgentMemoryRegistry memoryRegistry,
                       ObjectMapper objectMapper,
                       AgentDefinitionRepository definitionRepository) {
        this.flowRepository = flowRepository;
        this.stateMachineFactory = stateMachineFactory;
        this.orchestrator = orchestrator;
        this.instanceStore = instanceStore;
        this.memoryRegistry = memoryRegistry;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.definitionRepository = definitionRepository;
    }

    /**
     * 创建并执行一个 Agent 实例。
     *
     * @param definition     Agent 定义
     * @param inputs         本次调用入参
     * @param userId         归属用户ID
     * @param conversationId 关联会话ID
     * @return 执行后的编排上下文（含产物；失败时 {@link StateMachineOrchestrator#FAILED_ERROR_KEY} 有错误摘要）
     */
    public OrchestrationContext run(AgentDefinition definition, Map<String, Object> inputs,
                                    String userId, String conversationId) {
        return run(definition, inputs, userId, conversationId, null, null);
    }

    /**
     * 创建并执行一个 Agent 实例，可关联父实例（递归子 Agent，阶段 3）。
     *
     * @param definition       Agent 定义
     * @param inputs           本次调用入参
     * @param userId           归属用户ID
     * @param conversationId   关联会话ID
     * @param parentInstanceId 父实例标识（顶层为 null）
     * @param parentNodeCode   父实例中触发本子实例的 AgentNode 编码（顶层为 null）
     * @return 执行后的编排上下文
     */
    public OrchestrationContext run(AgentDefinition definition, Map<String, Object> inputs,
                                    String userId, String conversationId,
                                    String parentInstanceId, String parentNodeCode) {
        if (definition == null) {
            throw new OrchestrationException("Agent 定义不能为空");
        }
        if (!definition.isEnabled()) {
            throw new OrchestrationException("Agent[" + definition.getAgentCode() + "]已停用，不能新建实例");
        }

        // ① 取源 Flow 定义并编图（Agent 默认档案灌入 Flow 默认位，参与模型档案三层优先级定档）
        FlowDefinition flow = resolveFlow(definition);
        StateMachineGraph graph = stateMachineFactory.build(flow);

        // ② 序列化源图为 graph_snapshot（版本锁定核心）
        String graphSnapshot = serializeSnapshot(flow);

        // ③ 建实例（RUNNING），子实例落 parent 关联
        String instanceId = instanceStore == null ? null
                : instanceStore.create(definition, graphSnapshot, userId, conversationId, inputs,
                parentInstanceId, parentNodeCode);

        // ④ 组装初始 context：Import Memory 先写 → Inputs 后写（同名以 Inputs 为准）
        OrchestrationContext ctx = new OrchestrationContext(userId, conversationId);
        AgentMemoryPolicy policy = resolveMemoryPolicy(definition);
        importMemory(definition, policy, userId, conversationId, ctx);
        applyInputs(inputs, ctx);
        // 当前实例 id 写入 context，供递归 AGENT 节点落子实例的 parent_instance_id
        if (instanceId != null) {
            ctx.put(CURRENT_INSTANCE_KEY, instanceId);
        }
        // 本流程的递归深度上限写入 context，供 AGENT 节点深度治理（子递归时会覆盖为子流程的上限）
        ctx.put(MAX_AGENT_DEPTH_KEY, flow.getMaxAgentDepth());

        // ⑤ 执行内核（带落库监听器；无 store 时 NOOP）
        TransitionListener listener = instanceStore == null
                ? TransitionListener.NOOP : new StoreBackedTransitionListener(instanceStore);
        orchestrator.run(graph, ctx, instanceId, listener);

        // ⑥ Export Memory：仅当业务成功到达终态才导出（失败/挂起都不导出，挂起留待唤醒续跑到终态再导）
        exportIfTerminalSuccess(definition, policy, userId, conversationId, ctx, instanceId);
        return ctx;
    }

    /**
     * 仅当实例成功抵达终态（既非 FAILED 也非 SUSPENDED）时 Export Memory。挂起态不导出——等 signal 唤醒续跑
     * 到终态再由 {@link #signal} 触发导出。Export 失败只 warning 不翻盘终态。
     */
    private void exportIfTerminalSuccess(AgentDefinition definition, AgentMemoryPolicy policy,
                                         String userId, String conversationId,
                                         OrchestrationContext ctx, String instanceId) {
        if (ctx.contains(StateMachineOrchestrator.FAILED_ERROR_KEY)) {
            log.warn("Agent[{}] 实例[{}]以 FAILED 结束，跳过 Export Memory", definition.getAgentCode(), instanceId);
            return;
        }
        if (ctx.contains(StateMachineOrchestrator.SUSPENDED_KEY)) {
            log.info("Agent[{}] 实例[{}]挂起等 signal，暂不 Export Memory", definition.getAgentCode(), instanceId);
            return;
        }
        exportMemory(definition, policy, userId, conversationId, ctx);
    }

    /**
     * 便捷重载：按 agentCode 从仓储取定义后执行。
     *
     * @param repository     Agent 定义仓储
     * @param agentCode      Agent 编码
     * @param inputs         入参
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @return 执行后的上下文
     */
    public OrchestrationContext run(AgentDefinitionRepository repository, String agentCode,
                                    Map<String, Object> inputs, String userId, String conversationId) {
        AgentDefinition def = repository.find(agentCode);
        if (def == null) {
            throw new OrchestrationException("Agent 定义不存在: " + agentCode);
        }
        return run(def, inputs, userId, conversationId);
    }

    /* ===================== 挂起唤醒（signal，阶段 3） ===================== */

    /**
     * 唤醒一个挂起（SUSPENDED）的实例并从挂起态续跑（Human-in-the-loop 入口，阶段 3）。
     * <ol>
     *   <li><b>CAS 抢占</b>：以快照 lock_version 抢占推进权，只有 SUSPENDED 才能抢占，失败即拒绝——绝不并发跑同一实例。</li>
     *   <li><b>重建图</b>：从 graph_snapshot 反序列化 + 重新编图（绝不回查 node/edge 表，版本锁定）。</li>
     *   <li><b>恢复 context</b>：从 context_snapshot 反序列化回上下文（挂起时已全量刷）。</li>
     *   <li><b>事件裁决</b>：event 需命中 awaiting_events；命中则把 event/payload 写入 context 供出边 guard 裁决去向。</li>
     *   <li><b>续跑</b>：从挂起态的出边裁决推进，到达终态则 Export Memory。</li>
     * </ol>
     *
     * @param instanceId 挂起实例标识
     * @param event      外部事件名（如 approve/reject/request-changes）
     * @param payload    事件负载（写入 context 供 guard 求值，可空）
     * @return 续跑后的上下文（再次到达终态/失败/挂起）
     */
    public OrchestrationContext signal(String instanceId, String event, Map<String, Object> payload) {
        if (instanceStore == null) {
            throw new OrchestrationException("未配置 AgentInstanceStore，无法唤醒挂起实例");
        }
        AgentInstanceSnapshot snapshot = instanceStore.load(instanceId);
        if (snapshot == null) {
            throw new OrchestrationException("挂起实例不存在: " + instanceId);
        }
        if (!"SUSPENDED".equals(snapshot.status())) {
            throw new OrchestrationException("实例[" + instanceId + "]当前状态[" + snapshot.status()
                    + "]非 SUSPENDED，不能唤醒");
        }
        // 事件裁决：awaiting_events 非空时 event 必须命中其一（为空表示任意事件都可唤醒）
        List<String> awaiting = snapshot.awaitingEvents();
        if (awaiting != null && !awaiting.isEmpty()
                && (event == null || !awaiting.contains(event.trim().toUpperCase()))) {
            throw new OrchestrationException("事件[" + event + "]不在实例[" + instanceId
                    + "]的等待事件集" + awaiting + "中，拒绝唤醒");
        }
        // CAS 抢占：只有 SUSPENDED + lock_version 匹配才成 RUNNING，失败即拒绝（并发另一个 signal 已抢先）
        if (!instanceStore.acquireForResume(instanceId, snapshot.lockVersion())) {
            throw new OrchestrationException("实例[" + instanceId + "]已被其它 signal 抢占或状态已变，拒绝唤醒");
        }

        // 重建图 + 恢复 context
        StateMachineGraph graph = rebuildGraph(snapshot);
        OrchestrationContext ctx = new OrchestrationContext(snapshot.userId(), snapshot.conversationId());
        Map<String, Object> restored = snapshot.contextSnapshot();
        if (restored != null) {
            // 恢复业务产物，但剔除挂起时残留的控制键（__smSuspended），避免续跑后仍被判为挂起
            restored.forEach((k, v) -> {
                if (!StateMachineOrchestrator.SUSPENDED_KEY.equals(k)) {
                    ctx.put(k, v);
                }
            });
        }
        // event/payload 写入 context 供出边 guard（SpEL）裁决去向
        if (event != null) {
            ctx.put(SIGNAL_EVENT_KEY, event);
        }
        if (payload != null) {
            payload.forEach(ctx::put);
        }
        ctx.put(CURRENT_INSTANCE_KEY, instanceId);

        // 从挂起态续跑（挂起态节点已执行过，从其出边裁决继续）
        TransitionListener listener = new StoreBackedTransitionListener(instanceStore);
        orchestrator.resumeFrom(graph, ctx, instanceId, listener,
                snapshot.currentState(), snapshot.contextSnapshotSeq() + 1, snapshot.transitionCount());

        // 续跑抵终态则 Export Memory（取回定义拿 memory_config；仓储缺失则跳过导出）
        AgentDefinition definition = resolveDefinitionForExport(snapshot.agentCode());
        if (definition != null) {
            AgentMemoryPolicy policy = resolveMemoryPolicy(definition);
            exportIfTerminalSuccess(definition, policy, snapshot.userId(), snapshot.conversationId(),
                    ctx, instanceId);
        }
        return ctx;
    }

    private StateMachineGraph rebuildGraph(AgentInstanceSnapshot snapshot) {
        try {
            FlowDefinition flow = objectMapper.readValue(snapshot.graphSnapshot(), FlowDefinition.class);
            return stateMachineFactory.build(flow);
        } catch (Exception e) {
            throw new OrchestrationException("从 graph_snapshot 重建图失败（实例 "
                    + snapshot.instanceId() + "）: " + e.getMessage(), e);
        }
    }

    private AgentDefinition resolveDefinitionForExport(String agentCode) {
        if (definitionRepository == null) {
            return null;
        }
        try {
            return definitionRepository.find(agentCode);
        } catch (Exception e) {
            log.warn("signal 续跑取 Agent[{}]定义失败，跳过 Export: {}", agentCode, e.getMessage());
            return null;
        }
    }

    /* ===================== 内部：编图 / 快照 ===================== */

    private FlowDefinition resolveFlow(AgentDefinition definition) {
        FlowDefinition flow = flowRepository.findByCode(definition.getFlowCode());
        if (flow == null) {
            throw new OrchestrationException("Agent[" + definition.getAgentCode()
                    + "]引用的流程不存在: " + definition.getFlowCode());
        }
        // Agent 级默认档案作为 Flow 默认档案（节点级已在工厂内优先处理）——模型档案三层优先级的中间层
        if (definition.getDefaultProfileCode() != null && !definition.getDefaultProfileCode().isBlank()
                && (flow.getDefaultProfileCode() == null || flow.getDefaultProfileCode().isBlank())) {
            flow.setDefaultProfileCode(definition.getDefaultProfileCode());
        }
        return flow;
    }

    private String serializeSnapshot(FlowDefinition flow) {
        try {
            return objectMapper.writeValueAsString(flow);
        } catch (Exception e) {
            throw new OrchestrationException("序列化 graph_snapshot 失败: " + e.getMessage(), e);
        }
    }

    /* ===================== 内部：Memory Import / Export ===================== */

    private AgentMemoryPolicy resolveMemoryPolicy(AgentDefinition definition) {
        String json = definition.getMemoryConfig();
        if (json == null || json.isBlank()) {
            return AgentMemoryPolicy.DISABLED;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return AgentMemoryPolicy.fromMap(map);
        } catch (Exception e) {
            log.warn("Agent[{}] memory_config 解析失败，降级为不启用记忆: {}",
                    definition.getAgentCode(), e.getMessage());
            return AgentMemoryPolicy.DISABLED;
        }
    }

    /**
     * Import：按 importKeys 从长期记忆召回，写入 context.Variables。未启用记忆或无 registry 时跳过。
     * 简化点：每个 key 以其名作检索意图取 top1 的 content 写回 context[key]；真实检索算法由 LongTermMemory 实现决定。
     */
    private void importMemory(AgentDefinition definition, AgentMemoryPolicy policy,
                              String userId, String conversationId, OrchestrationContext ctx) {
        if (!policy.enabled() || memoryRegistry == null || policy.importKeys().isEmpty()
                || !memoryRegistry.contains(definition.getAgentCode())) {
            return;
        }
        AgentMemory memory = memoryRegistry.get(definition.getAgentCode());
        for (String key : policy.importKeys()) {
            try {
                List<MemoryRecord> hits = memory.recall(new MemoryQuery()
                        .setAgentCode(definition.getAgentCode())
                        .setUserId(userId)
                        .setConversationId(conversationId)
                        .setText(key)
                        .setTopK(1));
                if (hits != null && !hits.isEmpty() && hits.get(0).getContent() != null) {
                    ctx.put(key, hits.get(0).getContent());
                }
            } catch (Exception e) {
                log.warn("Agent[{}] Import 记忆键[{}]失败（忽略）: {}",
                        definition.getAgentCode(), key, e.getMessage());
            }
        }
    }

    /**
     * 应用 Inputs：后写覆盖 Import（同名键以 Inputs 为准）。
     */
    private void applyInputs(Map<String, Object> inputs, OrchestrationContext ctx) {
        if (inputs == null) {
            return;
        }
        for (Map.Entry<String, Object> e : inputs.entrySet()) {
            ctx.put(e.getKey(), e.getValue());
        }
    }

    /**
     * Export：实例结束时把导入键对应的 context 值按策略写回长期记忆。失败只记 warning，不翻盘终态。
     * 简化点：以 importKeys 作为导出键集合（导出 Agent 在 context 中沉淀的这些变量）；策略 Replace/Append/Summary
     * 中 Summary 的 LLM 摘要留待有真实需求时接入，当前按 Append 语义写入。
     */
    private void exportMemory(AgentDefinition definition, AgentMemoryPolicy policy,
                              String userId, String conversationId, OrchestrationContext ctx) {
        if (!policy.enabled() || memoryRegistry == null || policy.importKeys().isEmpty()
                || !memoryRegistry.contains(definition.getAgentCode())) {
            return;
        }
        AgentMemory memory = memoryRegistry.get(definition.getAgentCode());
        for (String key : policy.importKeys()) {
            Object value = ctx.get(key);
            if (value == null) {
                continue;
            }
            try {
                MemoryRecord record = new MemoryRecord()
                        .setAgentCode(definition.getAgentCode())
                        .setUserId(userId)
                        .setConversationId(conversationId)
                        .setType(MemoryType.SEMANTIC)
                        .setContent(String.valueOf(value));
                record.getMetadata().put("varKey", key);
                record.getMetadata().put("exportStrategy", policy.exportStrategy().name());
                memory.remember(record);
            } catch (Exception e) {
                // Export 是尽力而为的旁路，失败不影响已成功的业务终态
                log.warn("Agent[{}] Export 记忆键[{}]失败（不翻盘终态）: {}",
                        definition.getAgentCode(), key, e.getMessage());
            }
        }
    }
}
