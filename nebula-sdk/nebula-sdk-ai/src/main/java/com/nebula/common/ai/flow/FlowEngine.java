package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.OrchestrationGraph;
import com.nebula.common.ai.orchestration.Orchestrator;

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

    private final FlowDefinitionRepository flowRepository;

    private final FlowGraphFactory graphFactory;

    private final Orchestrator orchestrator;

    private final Map<String, OrchestrationGraph> graphCache = new ConcurrentHashMap<>();

    public FlowEngine(FlowDefinitionRepository flowRepository, FlowGraphFactory graphFactory, Orchestrator orchestrator) {
        this.flowRepository = flowRepository;
        this.graphFactory = graphFactory;
        this.orchestrator = orchestrator;
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
        return orchestrator.run(graph, ctx);
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
