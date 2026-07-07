package com.nebula.common.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.agent.AgentEngine;
import com.nebula.common.ai.agent.AgentInstanceStore;
import com.nebula.common.ai.agent.AgentNodeExecutor;
import com.nebula.common.ai.agent.InMemoryAgentDefinitionRepository;
import com.nebula.common.ai.agent.tool.DefaultToolCallingService;
import com.nebula.common.ai.agent.tool.ToolCallingService;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.memory.AgentMemoryRegistry;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinitionRepository;
import com.nebula.common.ai.flow.FlowEngine;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.flow.InMemoryFlowDefinitionRepository;
import com.nebula.common.ai.flow.InMemoryModelProfileRepository;
import com.nebula.common.ai.flow.ModelProfileRepository;
import com.nebula.common.ai.flow.NoOpNodeExecutor;
import com.nebula.common.ai.flow.PromptNodeExecutor;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolNodeExecutor;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.flow.tool.EchoToolDefinition;
import com.nebula.common.ai.flow.tool.HttpToolDefinition;
import com.nebula.common.ai.flow.tool.HttpToolProperties;
import com.nebula.common.ai.orchestration.Orchestrator;
import com.nebula.common.ai.orchestration.RunStateStore;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import com.nebula.common.ai.properties.AiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 配置化流程编排自动装配
 * 在 AI 运行时已装配（存在 {@link AiService}）的前提下，提供流程引擎所需的全部组件：条件编译器、
 * 模型档案/流程定义仓储（默认内存实现，业务可覆盖为数据库实现）、提示词节点执行器、流程图工厂与流程引擎。
 *
 * @author nebula
 */
@AutoConfiguration(after = {AiAutoConfiguration.class, AiAgentAutoConfiguration.class})
@ConditionalOnBean(AiService.class)
@EnableConfigurationProperties(HttpToolProperties.class)
public class FlowAutoConfiguration {

    /**
     * 条件编译器（SpEL）
     */
    @Bean
    @ConditionalOnMissingBean
    public ConditionCompiler conditionCompiler() {
        return new ConditionCompiler();
    }

    /**
     * 模型档案仓储，默认内存实现
     */
    @Bean
    @ConditionalOnMissingBean
    public ModelProfileRepository modelProfileRepository() {
        return new InMemoryModelProfileRepository();
    }

    /**
     * 流程定义仓储，默认内存实现
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowDefinitionRepository flowDefinitionRepository() {
        return new InMemoryFlowDefinitionRepository();
    }

    /**
     * 提示词节点执行器（PROMPT 类型）
     *
     * @param aiService         AI服务
     * @param profileRepository 模型档案仓储
     * @param objectMapper      JSON处理器（缺省自建）
     * @return 提示词节点执行器
     */
    @Bean
    @ConditionalOnMissingBean(PromptNodeExecutor.class)
    public PromptNodeExecutor promptNodeExecutor(AiService aiService,
                                                 ModelProfileRepository profileRepository,
                                                 ObjectProvider<ObjectMapper> objectMapper) {
        return new PromptNodeExecutor(aiService, profileRepository, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * 结构性节点空执行器（START/END/IF/JOIN）。这些节点不承载业务动作——入口/出口/分流/汇聚均由
     * 图结构与出边 SpEL 条件承载，故用同一 {@link NoOpNodeExecutor} 以不同 type 注册多个 Bean 占位，
     * 使 {@link FlowGraphFactory} 能为其构图。业务方如需覆盖某类型（如 END 落 outputs），
     * 声明同 type 的自有执行器即可（工厂按 type 去重，先注册者生效）。
     *
     * @return 开始节点执行器
     */
    @Bean
    public NoOpNodeExecutor startNodeExecutor() {
        return new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_START);
    }

    /**
     * 结束节点空执行器
     *
     * @return 结束节点执行器
     */
    @Bean
    public NoOpNodeExecutor endNodeExecutor() {
        return new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_END);
    }

    /**
     * 条件分支节点空执行器（分流靠出边条件）
     *
     * @return 条件分支节点执行器
     */
    @Bean
    public NoOpNodeExecutor ifNodeExecutor() {
        return new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_IF);
    }

    /**
     * 并行汇聚节点空执行器（汇聚靠入度归零）
     *
     * @return 汇聚节点执行器
     */
    @Bean
    public NoOpNodeExecutor joinNodeExecutor() {
        return new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_JOIN);
    }

    /**
     * 内置回声工具（示例）。业务方定义自有工具时同样实现 {@link ToolDefinition} 并注册为 Bean 即可。
     *
     * @return 回声工具
     */
    @Bean
    @ConditionalOnMissingBean(EchoToolDefinition.class)
    public EchoToolDefinition echoToolDefinition() {
        return new EchoToolDefinition();
    }

    /**
     * 内置 HTTP 请求工具。持有专用连接池与线程池，Bean 销毁时经 {@code destroyMethod=shutdown} 释放。
     *
     * @param httpToolProperties HTTP 工具配置（nebula.ai.tool.http.*）
     * @param objectMapper       JSON 处理器（缺省自建，序列化对象请求体）
     * @return HTTP 请求工具
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(HttpToolDefinition.class)
    public HttpToolDefinition httpToolDefinition(HttpToolProperties httpToolProperties,
                                                 ObjectProvider<ObjectMapper> objectMapper) {
        return new HttpToolDefinition(httpToolProperties, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * 工具注册表，聚合容器中全部工具定义
     *
     * @param definitions 工具定义
     * @return 工具注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(ObjectProvider<ToolDefinition> definitions) {
        return new ToolRegistry(definitions.orderedStream().toList());
    }

    /**
     * 工具节点执行器（TOOL 类型）
     *
     * @param toolRegistry 工具注册表
     * @return 工具节点执行器
     */
    @Bean
    @ConditionalOnMissingBean(ToolNodeExecutor.class)
    public ToolNodeExecutor toolNodeExecutor(ToolRegistry toolRegistry) {
        return new ToolNodeExecutor(toolRegistry);
    }

    /**
     * 工具调用闭环服务（function-calling loop）。
     * 驱动「调模型 → 模型自主选工具 → 执行 → 回灌 → 再调」的循环，内置迭代上限/异常隔离/白名单/审计/超时治理。
     * 持有工具执行线程池，Bean 销毁时经 {@code destroyMethod=shutdown} 释放。
     *
     * @param aiService    AI服务（每轮模型调用复用其过滤器链与日志）
     * @param toolRegistry 工具注册表
     * @param objectMapper JSON 处理器（解析模型生成的工具入参、序列化工具产物）
     * @param aiProperties AI配置属性（取 tool-calling 迭代上限与超时）
     * @return 工具调用闭环服务
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public ToolCallingService toolCallingService(AiService aiService,
                                                 ToolRegistry toolRegistry,
                                                 ObjectProvider<ObjectMapper> objectMapper,
                                                 AiProperties aiProperties) {
        return new DefaultToolCallingService(aiService, toolRegistry,
                objectMapper.getIfAvailable(ObjectMapper::new), aiProperties);
    }

    /**
     * 流程图工厂，聚合容器中全部节点执行器
     *
     * @param executors         节点执行器
     * @param conditionCompiler 条件编译器
     * @return 流程图工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowGraphFactory flowGraphFactory(ObjectProvider<FlowNodeExecutor> executors,
                                             ConditionCompiler conditionCompiler) {
        return new FlowGraphFactory(executors.orderedStream().toList(), conditionCompiler);
    }

    /**
     * 状态机图工厂，聚合容器中全部节点执行器（与 {@link FlowGraphFactory} 复用同一批执行器与条件编译器）
     *
     * @param executors         节点执行器
     * @param conditionCompiler 条件编译器
     * @return 状态机图工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowStateMachineFactory flowStateMachineFactory(ObjectProvider<FlowNodeExecutor> executors,
                                                           ConditionCompiler conditionCompiler) {
        return new FlowStateMachineFactory(executors.orderedStream().toList(), conditionCompiler);
    }

    /**
     * 状态机节点超时熔断线程池（daemon，不阻止 JVM 退出）。仅当节点配了 {@code stateConfig.timeoutMs} 时使用。
     *
     * @return 缓存线程池，Bean 销毁时经 {@code shutdown} 释放
     */
    @Bean(name = "stateMachineTimeoutExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "stateMachineTimeoutExecutor")
    public ExecutorService stateMachineTimeoutExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicLong seq = new AtomicLong();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "sm-timeout-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newCachedThreadPool(factory);
    }

    /**
     * 状态机编排内核（阶段 1：单点状态推进 + 重试/超时/错误转移 + max_transitions，不含挂起/落库）
     *
     * @param timeoutExecutor 超时熔断线程池
     * @return 状态机编排器
     */
    @Bean
    @ConditionalOnMissingBean
    public StateMachineOrchestrator stateMachineOrchestrator(ExecutorService stateMachineTimeoutExecutor) {
        return new StateMachineOrchestrator(stateMachineTimeoutExecutor);
    }

    /**
     * 流程引擎
     *
     * @param flowRepository       流程定义仓储
     * @param graphFactory         流程图工厂（DAG）
     * @param orchestrator         DAG 编排器
     * @param runStateStore        编排状态存储（可空，DAG 续跑用）
     * @param stateMachineFactory  状态机图工厂
     * @param stateMachineOrchestrator 状态机编排内核
     * @return 流程引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowEngine flowEngine(FlowDefinitionRepository flowRepository,
                                 FlowGraphFactory graphFactory,
                                 Orchestrator orchestrator,
                                 ObjectProvider<RunStateStore> runStateStore,
                                 FlowStateMachineFactory stateMachineFactory,
                                 StateMachineOrchestrator stateMachineOrchestrator) {
        return new FlowEngine(flowRepository, graphFactory, orchestrator,
                runStateStore.getIfAvailable(), stateMachineFactory, stateMachineOrchestrator);
    }

    /**
     * Agent 定义仓储，默认内存实现。业务侧（nebula-sdk-ai-flow）可声明数据库实现（读 ai_agent 表）覆盖。
     *
     * @return Agent 定义仓储
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentDefinitionRepository agentDefinitionRepository() {
        return new InMemoryAgentDefinitionRepository();
    }

    /**
     * Agent 执行门面（阶段 2）：收口"创建实例 → Import → 执行状态机 → Export → 落终态"。
     * 复用状态机图工厂与内核；注入可选 {@link AgentInstanceStore}（DB 实现由 nebula-sdk-ai-flow 提供，缺失时纯内存执行）
     * 与可选 {@link AgentMemoryRegistry}（缺失时不启用记忆钩子）。
     *
     * @param flowRepository           流程定义仓储
     * @param stateMachineFactory      状态机图工厂
     * @param stateMachineOrchestrator 状态机编排内核
     * @param instanceStore            Agent 实例存储（可空）
     * @param memoryRegistry           Agent 记忆注册表（可空）
     * @param objectMapper             JSON 处理器（graph_snapshot 序列化、memory_config 解析）
     * @return Agent 执行门面
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentEngine agentEngine(FlowDefinitionRepository flowRepository,
                                   FlowStateMachineFactory stateMachineFactory,
                                   StateMachineOrchestrator stateMachineOrchestrator,
                                   ObjectProvider<AgentInstanceStore> instanceStore,
                                   ObjectProvider<AgentMemoryRegistry> memoryRegistry,
                                   ObjectProvider<ObjectMapper> objectMapper,
                                   AgentDefinitionRepository agentDefinitionRepository) {
        return new AgentEngine(flowRepository, stateMachineFactory, stateMachineOrchestrator,
                instanceStore.getIfAvailable(), memoryRegistry.getIfAvailable(),
                objectMapper.getIfAvailable(ObjectMapper::new), agentDefinitionRepository);
    }

    /**
     * Agent 节点执行器（AGENT 类型，递归子 Agent，阶段 3）。注册进执行器列表后，含 {@code nodeType=AGENT}
     * 节点的流程即可递归引用另一个 Agent。用 {@link ObjectProvider} 延迟取 {@link AgentEngine}——打破
     * "AgentEngine → 状态机工厂 → 执行器列表 → AgentNodeExecutor → AgentEngine" 的构造期循环依赖。
     *
     * @param agentEngine             Agent 执行门面（延迟注入）
     * @param agentDefinitionRepository Agent 定义仓储（按 refAgentCode 取子 Agent 定义）
     * @return Agent 节点执行器
     */
    @Bean
    @ConditionalOnMissingBean(AgentNodeExecutor.class)
    public AgentNodeExecutor agentNodeExecutor(ObjectProvider<AgentEngine> agentEngine,
                                               AgentDefinitionRepository agentDefinitionRepository) {
        return new AgentNodeExecutor(agentEngine::getObject, agentDefinitionRepository);
    }
}
