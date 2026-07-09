package com.nebula.common.ai.flow;

import com.nebula.common.ai.flow.input.InputValidationError;
import com.nebula.common.ai.flow.input.InputValidationException;
import com.nebula.common.ai.flow.input.StartInputSpec;
import com.nebula.common.ai.flow.input.StartInputSpecParser;
import com.nebula.common.ai.flow.input.StartInputValidator;
import com.nebula.common.ai.orchestration.ContextKeys;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.OrchestrationGraph;
import com.nebula.common.ai.orchestration.Orchestrator;
import com.nebula.common.ai.orchestration.RunContext;
import com.nebula.common.ai.orchestration.RunSnapshot;
import com.nebula.common.ai.orchestration.RunStateStore;
import com.nebula.common.ai.orchestration.RunStatus;
import com.nebula.common.ai.orchestration.statemachine.StateMachineGraph;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;

import java.util.List;
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
     * 上下文保留键：当前流程编码，供节点执行器用作 agentCode。
     * 别名转发到 {@link ContextKeys.System#FLOW_CODE}（唯一权威出处）。
     */
    public static final String FLOW_CODE_KEY = ContextKeys.System.FLOW_CODE;

    /**
     * 上下文保留键：当前执行实例 runId，供上层（如 REST）读取以发起续跑。
     * 别名转发到 {@link ContextKeys.System#RUN_ID}。
     */
    public static final String RUN_ID_KEY = ContextKeys.System.RUN_ID;

    /**
     * 引擎类型：状态机内核（对应 {@code ai_flow.engine_type}）
     */
    public static final String ENGINE_STATE_MACHINE = "STATE_MACHINE";

    private final FlowDefinitionRepository flowRepository;

    private final FlowGraphFactory graphFactory;

    private final Orchestrator orchestrator;

    /**
     * 编排执行状态存储，可空。为空时不持久化执行进度、不支持续跑，run 行为与历史一致。
     */
    private final RunStateStore runStateStore;

    /**
     * 状态机图工厂，可空。为空时不支持 {@code engine_type=STATE_MACHINE} 的流程。
     */
    private final FlowStateMachineFactory stateMachineFactory;

    /**
     * 状态机内核，可空。与 {@link #stateMachineFactory} 成对装配。
     */
    private final StateMachineOrchestrator stateMachineOrchestrator;

    private final Map<String, OrchestrationGraph> graphCache = new ConcurrentHashMap<>();

    private final Map<String, StateMachineGraph> stateMachineCache = new ConcurrentHashMap<>();

    /**
     * START 入参规格解析器 + 校验器（无依赖、无状态，自建即可）。运行前按 START 节点声明的 schema
     * 校验外部入参，堵住前端表单之外入口（REST/子 Agent/cron）的零校验缺口。无入参声明的流程自动跳过。
     */
    private final StartInputSpecParser inputSpecParser = new StartInputSpecParser();

    private final StartInputValidator inputValidator = new StartInputValidator();

    public FlowEngine(FlowDefinitionRepository flowRepository, FlowGraphFactory graphFactory, Orchestrator orchestrator) {
        this(flowRepository, graphFactory, orchestrator, null);
    }

    public FlowEngine(FlowDefinitionRepository flowRepository, FlowGraphFactory graphFactory,
                      Orchestrator orchestrator, RunStateStore runStateStore) {
        this(flowRepository, graphFactory, orchestrator, runStateStore, null, null);
    }

    public FlowEngine(FlowDefinitionRepository flowRepository, FlowGraphFactory graphFactory,
                      Orchestrator orchestrator, RunStateStore runStateStore,
                      FlowStateMachineFactory stateMachineFactory,
                      StateMachineOrchestrator stateMachineOrchestrator) {
        this.flowRepository = flowRepository;
        this.graphFactory = graphFactory;
        this.orchestrator = orchestrator;
        this.runStateStore = runStateStore;
        this.stateMachineFactory = stateMachineFactory;
        this.stateMachineOrchestrator = stateMachineOrchestrator;
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

        // 入参校验：按 START 节点声明的 schema 校验外部入参（聚合全部违规一次抛出），灌入前拦截
        List<StartInputSpec> specs = inputSpecParser.parse(def);
        List<InputValidationError> errors = inputValidator.validate(specs, input);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        OrchestrationContext ctx = new OrchestrationContext(userId, conversationId);
        if (input != null) {
            input.forEach(ctx::put);
        }
        ctx.put(FLOW_CODE_KEY, flowCode);

        // 按引擎类型分流：状态机内核走单点状态推进（阶段 1 不落库、不续跑）
        if (isStateMachine(def)) {
            return runStateMachine(def, ctx);
        }

        OrchestrationGraph graph = graphCache.computeIfAbsent(cacheKey(def), k -> graphFactory.build(def));
        // 装配了状态存储时：创建执行实例并以 runId 驱动持久化编排，使中断后可经 resume 续跑
        if (runStateStore != null) {
            String runId = runStateStore.startRun(flowCode, def.getVersion(), userId, conversationId, input);
            ctx.put(RUN_ID_KEY, runId);
            return orchestrator.run(graph, ctx, new RunContext(runId, flowCode, def.getVersion(), false));
        }
        return orchestrator.run(graph, ctx);
    }

    /**
     * 状态机内核执行：编译（缓存）为状态机图，交 {@link StateMachineOrchestrator} 单点推进。
     * 阶段 1 纯内存执行，不落库、不支持续跑/挂起（见文档第十二章落地路线）。
     */
    private OrchestrationContext runStateMachine(FlowDefinition def, OrchestrationContext ctx) {
        if (stateMachineFactory == null || stateMachineOrchestrator == null) {
            throw new OrchestrationException("流程[" + def.getFlowCode()
                    + "]声明 engine_type=STATE_MACHINE，但未装配状态机内核（FlowStateMachineFactory/StateMachineOrchestrator）");
        }
        StateMachineGraph graph = stateMachineCache.computeIfAbsent(cacheKey(def), k -> stateMachineFactory.build(def));
        return stateMachineOrchestrator.run(graph, ctx);
    }

    /**
     * 该流程是否使用状态机内核
     */
    private boolean isStateMachine(FlowDefinition def) {
        return ENGINE_STATE_MACHINE.equalsIgnoreCase(def.getEngineType());
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
        if (isStateMachine(def)) {
            // 状态机实例落 ai_agent_instance（graph_snapshot 自包含），走独立续跑入口，不复用 ai_flow_run 语义（阶段 2）
            throw new OrchestrationException("流程[" + snapshot.flowCode()
                    + "]为状态机内核，其续跑走状态机实例专用入口，不支持 DAG 续跑（阶段 2 能力）");
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
            stateMachineCache.keySet().removeIf(key -> key.startsWith(flowCode + ":"));
        }
    }

    private String cacheKey(FlowDefinition def) {
        return def.getFlowCode() + ":" + def.getVersion();
    }
}
