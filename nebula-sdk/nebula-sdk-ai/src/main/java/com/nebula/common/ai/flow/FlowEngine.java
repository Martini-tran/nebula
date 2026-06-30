package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.OrchestrationGraph;
import com.nebula.common.ai.orchestration.Orchestrator;
import com.nebula.common.ai.orchestration.RunContext;
import com.nebula.common.ai.orchestration.RunSnapshot;
import com.nebula.common.ai.orchestration.RunStateStore;
import com.nebula.common.ai.orchestration.RunStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程引擎
 * 配置化编排的统一入口：按编码加载流程定义 → 编译为编排图（按 {@code flowCode+version} 缓存）→
 * 以初始输入构建上下文 → 交由 {@link Orchestrator} 执行 → 返回承载各节点产物的上下文。
 *
 * <p>流程定义变更后调用 {@link #evict(String)} 失效缓存，使下次运行重新编译。
 *
 * @author nebula
 */
public class FlowEngine {

    /**
     * 上下文保留键：当前流程编码，供节点执行器用作 agentCode
     */
    public static final String FLOW_CODE_KEY = "__flowCode";

    /**
     * 上下文保留键：当前执行实例 runId，供上层（如 REST）读取以发起续跑
     */
    public static final String RUN_ID_KEY = "__runId";

    private final FlowDefinitionRepository flowRepository;

    private final FlowGraphFactory graphFactory;

    private final Orchestrator orchestrator;

    /**
     * 编排执行状态存储，可空。为空时不持久化执行进度、不支持续跑，run 行为与历史一致。
     */
    private final RunStateStore runStateStore;

    private final Map<String, OrchestrationGraph> graphCache = new ConcurrentHashMap<>();

    public FlowEngine(FlowDefinitionRepository flowRepository, FlowGraphFactory graphFactory, Orchestrator orchestrator) {
        this(flowRepository, graphFactory, orchestrator, null);
    }

    public FlowEngine(FlowDefinitionRepository flowRepository, FlowGraphFactory graphFactory,
                      Orchestrator orchestrator, RunStateStore runStateStore) {
        this.flowRepository = flowRepository;
        this.graphFactory = graphFactory;
        this.orchestrator = orchestrator;
        this.runStateStore = runStateStore;
    }

    /**
     * 运行流程
     *
     * @param flowCode       流程编码
     * @param input          初始输入（写入上下文，供首个节点的模板引用）
     * @param userId         归属用户ID
     * @param conversationId 关联会话ID
     * @return 执行后的编排上下文
     */
    public OrchestrationContext run(String flowCode, Map<String, Object> input, String userId, String conversationId) {
        FlowDefinition def = flowRepository == null ? null : flowRepository.findByCode(flowCode);
        if (def == null) {
            throw new OrchestrationException("未找到流程定义: " + flowCode);
        }
        OrchestrationGraph graph = graphCache.computeIfAbsent(cacheKey(def), k -> graphFactory.build(def));

        OrchestrationContext ctx = new OrchestrationContext(userId, conversationId);
        if (input != null) {
            input.forEach(ctx::put);
        }
        ctx.put(FLOW_CODE_KEY, flowCode);

        // 装配了状态存储时：创建执行实例并以 runId 驱动持久化编排，使中断后可经 resume 续跑
        if (runStateStore != null) {
            String runId = runStateStore.startRun(flowCode, def.getVersion(), userId, conversationId, input);
            ctx.put(RUN_ID_KEY, runId);
            return orchestrator.run(graph, ctx, new RunContext(runId, flowCode, def.getVersion(), false));
        }
        return orchestrator.run(graph, ctx);
    }

    /**
     * 续跑一个已落库的执行实例。
     * 从 {@link RunStateStore} 读实例快照，加载流程定义、编图，恢复用户/会话与已执行进度，
     * 跳过已完成节点从断点继续。需装配状态存储；实例不存在或已成功时拒绝。
     *
     * @param runId 执行实例标识
     * @return 续跑后的编排上下文
     */
    public OrchestrationContext resume(String runId) {
        if (runStateStore == null) {
            throw new OrchestrationException("未启用编排状态持久化，无法续跑（需提供 RunStateStore 实现）");
        }
        RunSnapshot snapshot = runStateStore.load(runId);
        if (snapshot == null) {
            throw new OrchestrationException("未找到执行实例: " + runId);
        }
        if (snapshot.status() == RunStatus.SUCCESS) {
            throw new OrchestrationException("执行实例[" + runId + "]已成功完成，无需续跑");
        }
        FlowDefinition def = flowRepository == null ? null : flowRepository.findByCode(snapshot.flowCode());
        if (def == null) {
            throw new OrchestrationException("未找到流程定义: " + snapshot.flowCode());
        }
        OrchestrationGraph graph = graphCache.computeIfAbsent(cacheKey(def), k -> graphFactory.build(def));

        OrchestrationContext ctx = new OrchestrationContext(snapshot.userId(), snapshot.conversationId());
        ctx.put(FLOW_CODE_KEY, snapshot.flowCode());
        ctx.put(RUN_ID_KEY, runId);
        // 续跑标记为 true：编排器据此从快照恢复 executed 与产物
        return orchestrator.run(graph, ctx, new RunContext(runId, snapshot.flowCode(), def.getVersion(), true));
    }

    /**
     * 运行流程（无用户/会话标识）
     *
     * @param flowCode 流程编码
     * @param input    初始输入
     * @return 执行后的编排上下文
     */
    public OrchestrationContext run(String flowCode, Map<String, Object> input) {
        return run(flowCode, input, null, null);
    }

    /**
     * 失效指定流程的全部版本缓存
     *
     * @param flowCode 流程编码
     */
    public void evict(String flowCode) {
        if (flowCode != null) {
            graphCache.keySet().removeIf(key -> key.startsWith(flowCode + ":"));
        }
    }

    private String cacheKey(FlowDefinition def) {
        return def.getFlowCode() + ":" + def.getVersion();
    }
}
