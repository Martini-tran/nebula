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

    private final FlowDefinitionRepository flowRepository;

    private final FlowStateMachineFactory stateMachineFactory;

    private final StateMachineOrchestrator orchestrator;

    private final AgentInstanceStore instanceStore;

    private final AgentMemoryRegistry memoryRegistry;

    private final ObjectMapper objectMapper;

    public AgentEngine(FlowDefinitionRepository flowRepository,
                       FlowStateMachineFactory stateMachineFactory,
                       StateMachineOrchestrator orchestrator,
                       AgentInstanceStore instanceStore,
                       AgentMemoryRegistry memoryRegistry,
                       ObjectMapper objectMapper) {
        this.flowRepository = flowRepository;
        this.stateMachineFactory = stateMachineFactory;
        this.orchestrator = orchestrator;
        this.instanceStore = instanceStore;
        this.memoryRegistry = memoryRegistry;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
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

        // ③ 建实例（RUNNING）
        String instanceId = instanceStore == null ? null
                : instanceStore.create(definition, graphSnapshot, userId, conversationId, inputs);

        // ④ 组装初始 context：Import Memory 先写 → Inputs 后写（同名以 Inputs 为准）
        OrchestrationContext ctx = new OrchestrationContext(userId, conversationId);
        AgentMemoryPolicy policy = resolveMemoryPolicy(definition);
        importMemory(definition, policy, userId, conversationId, ctx);
        applyInputs(inputs, ctx);

        // ⑤ 执行内核（带落库监听器；无 store 时 NOOP）
        TransitionListener listener = instanceStore == null
                ? TransitionListener.NOOP : new StoreBackedTransitionListener(instanceStore);
        orchestrator.run(graph, ctx, instanceId, listener);

        // ⑥ Export Memory（失败不翻盘终态）
        boolean failed = ctx.contains(StateMachineOrchestrator.FAILED_ERROR_KEY);
        if (!failed) {
            exportMemory(definition, policy, userId, conversationId, ctx);
        } else {
            log.warn("Agent[{}] 实例[{}]以 FAILED 结束，跳过 Export Memory", definition.getAgentCode(), instanceId);
        }
        return ctx;
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
