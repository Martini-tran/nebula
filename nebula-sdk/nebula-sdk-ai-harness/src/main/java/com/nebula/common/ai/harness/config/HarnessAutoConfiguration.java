package com.nebula.common.ai.harness.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.config.FlowAutoConfiguration;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.CondGroupCompiler;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.flow.store.AiFlowDraftMapper;
import com.nebula.common.ai.flow.store.AiHarnessConfirmationMapper;
import com.nebula.common.ai.flow.store.AiHarnessOperationMapper;
import com.nebula.common.ai.flow.store.AiFlowStoreAutoConfiguration;
import com.nebula.common.ai.harness.conversation.HarnessExampleProvider;
import com.nebula.common.ai.harness.conversation.HarnessPromptProvider;
import com.nebula.common.ai.harness.conversation.HarnessToolAuthorizer;
import com.nebula.common.ai.harness.runtime.FlowGenerationHarness;
import com.nebula.common.ai.harness.runtime.HarnessToolScheduler;
import com.nebula.common.ai.harness.realrun.DatabaseDraftConfirmationStore;
import com.nebula.common.ai.harness.realrun.DatabaseHarnessOperationStore;
import com.nebula.common.ai.harness.realrun.DraftConfirmationStore;
import com.nebula.common.ai.harness.realrun.HarnessOperationStore;
import com.nebula.common.ai.harness.draft.DatabaseDraftStore;
import com.nebula.common.ai.harness.draft.DraftApplicationService;
import com.nebula.common.ai.harness.draft.DraftCommitter;
import com.nebula.common.ai.harness.draft.DraftFieldValidator;
import com.nebula.common.ai.harness.draft.DraftNodeConverter;
import com.nebula.common.ai.harness.draft.DraftStore;
import com.nebula.common.ai.harness.draft.FlowDefinitionCodec;
import com.nebula.common.ai.harness.tool.AddNodeToolDefinition;
import com.nebula.common.ai.harness.tool.ConnectToolDefinition;
import com.nebula.common.ai.harness.tool.CommitDraftToolDefinition;
import com.nebula.common.ai.harness.tool.CreateDraftToolDefinition;
import com.nebula.common.ai.harness.tool.DisconnectToolDefinition;
import com.nebula.common.ai.harness.tool.ListNodeTypesToolDefinition;
import com.nebula.common.ai.harness.tool.ReadDraftToolDefinition;
import com.nebula.common.ai.harness.tool.RemoveNodeToolDefinition;
import com.nebula.common.ai.harness.tool.SimulateDraftToolDefinition;
import com.nebula.common.ai.harness.tool.UpdateDraftMetadataToolDefinition;
import com.nebula.common.ai.harness.tool.UpdateNodeToolDefinition;
import com.nebula.common.ai.harness.tool.ValidateDraftToolDefinition;
import com.nebula.common.ai.harness.validate.CommonRules;
import com.nebula.common.ai.harness.validate.DagRuleSet;
import com.nebula.common.ai.harness.validate.DraftValidator;
import com.nebula.common.ai.harness.validate.EngineRuleSet;
import com.nebula.common.ai.harness.validate.StateMachineRuleSet;
import com.nebula.common.ai.harness.simulate.DagSimulator;
import com.nebula.common.ai.harness.simulate.DraftSimulator;
import com.nebula.common.ai.harness.simulate.EngineSimulator;
import com.nebula.common.ai.harness.simulate.PlaceholderSimulationNodeExecutor;
import com.nebula.common.ai.harness.simulate.SimulationConditionEvaluator;
import com.nebula.common.ai.harness.simulate.SimulationExecutorRegistry;
import com.nebula.common.ai.harness.simulate.SimulationInputValidator;
import com.nebula.common.ai.harness.simulate.SimulationLoopDriver;
import com.nebula.common.ai.harness.simulate.SimulationNodeExecutor;
import com.nebula.common.ai.harness.simulate.StateMachineSimulator;
import com.nebula.common.ai.harness.simulate.StructuralSimulationNodeExecutor;
import com.nebula.common.ai.properties.AiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流程生成 Harness 自动装配。
 *
 * <p><b>切勿对 Mapper 用 {@code @ConditionalOnBean}</b>（如 {@code @ConditionalOnBean(AiFlowDraftMapper.class)}）：
 * Mapper 的 BeanDefinition 由 {@code MapperScannerConfigurer} 注册，它是一个既不实现 {@code PriorityOrdered}
 * 也不实现 {@code Ordered} 的 {@code BeanDefinitionRegistryPostProcessor}，执行时机排在
 * {@code ConfigurationClassPostProcessor} 之后；而自动装配的条件在 {@code ConfigurationClassPostProcessor}
 * 内就已求值完毕——此刻容器里还没有任何 Mapper 定义，条件恒为假，整串 Harness Bean 会被静默跳过
 * （表现为启动期报 {@code required a bean of type 'DraftRealRunService' that could not be found}）。
 * Mapper 只能以构造参数注入（在 Bean 实例化阶段解析，那时已注册完毕）。
 *
 * <p>本模块编译期即依赖 {@code nebula-sdk-ai-flow} 的落库层，无「无数据库运行」形态，故不再对 Mapper 加条件；
 * 类级 {@code @ConditionalOnBean({AiService, ToolRegistry})} 的两个类型均由先序自动装配提供，可正常求值。
 *
 * @author nebula
 */
@AutoConfiguration(after = {FlowAutoConfiguration.class, AiFlowStoreAutoConfiguration.class})
@ConditionalOnBean({AiService.class, ToolRegistry.class})
@EnableConfigurationProperties({HarnessDraftProperties.class, HarnessSimulationProperties.class,
        HarnessCommitProperties.class, HarnessRealRunProperties.class})
public class HarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ListNodeTypesToolDefinition listNodeTypesToolDefinition(
            ObjectProvider<FlowNodeExecutor> executorProvider) {
        return new ListNodeTypesToolDefinition(executorProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowDefinitionCodec flowDefinitionCodec(ObjectProvider<ObjectMapper> objectMapper) {
        return new FlowDefinitionCodec(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public DraftStore draftStore(AiFlowDraftMapper mapper, FlowDefinitionCodec codec) {
        return new DatabaseDraftStore(mapper, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    public DraftNodeConverter draftNodeConverter(ObjectProvider<ObjectMapper> objectMapper) {
        return new DraftNodeConverter(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public DraftFieldValidator draftFieldValidator(ObjectProvider<ToolRegistry> toolRegistry,
                                                   ObjectProvider<AgentDefinitionRepository> agentRepository,
                                                   ObjectProvider<FlowNodeExecutor> nodeExecutors,
                                                   FlowDefinitionCodec codec,
                                                   HarnessDraftProperties properties) {
        return new DraftFieldValidator(toolRegistry, agentRepository, nodeExecutors, codec, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommonRules commonDraftRules() {
        return new CommonRules();
    }

    @Bean
    @ConditionalOnMissingBean(name = "dagDraftRuleSet")
    public DagRuleSet dagDraftRuleSet() {
        return new DagRuleSet();
    }

    @Bean
    @ConditionalOnMissingBean(name = "stateMachineDraftRuleSet")
    public StateMachineRuleSet stateMachineDraftRuleSet() {
        return new StateMachineRuleSet();
    }

    @Bean
    @ConditionalOnMissingBean
    public DraftValidator draftValidator(DraftFieldValidator fieldValidator,
                                         CommonRules commonRules,
                                         ObjectProvider<EngineRuleSet> ruleSets,
                                         FlowGraphFactory graphFactory,
                                         FlowStateMachineFactory stateMachineFactory,
                                         ConditionCompiler conditionCompiler,
                                         CondGroupCompiler condGroupCompiler,
                                         FlowDefinitionCodec codec) {
        return new DraftValidator(fieldValidator, commonRules, ruleSets.orderedStream().toList(),
                graphFactory, stateMachineFactory, conditionCompiler, condGroupCompiler, codec);
    }

    @Bean
    @ConditionalOnMissingBean(name = "structuralSimulationNodeExecutor")
    public StructuralSimulationNodeExecutor structuralSimulationNodeExecutor() {
        return new StructuralSimulationNodeExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "placeholderSimulationNodeExecutor")
    public PlaceholderSimulationNodeExecutor placeholderSimulationNodeExecutor() {
        return new PlaceholderSimulationNodeExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public SimulationExecutorRegistry simulationExecutorRegistry(
            ObjectProvider<SimulationNodeExecutor> executors) {
        return new SimulationExecutorRegistry(executors.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public SimulationConditionEvaluator simulationConditionEvaluator(ConditionCompiler conditionCompiler) {
        return new SimulationConditionEvaluator(conditionCompiler);
    }

    @Bean
    @ConditionalOnMissingBean
    public SimulationLoopDriver simulationLoopDriver(HarnessSimulationProperties properties,
                                                     SimulationExecutorRegistry registry) {
        return new SimulationLoopDriver(properties, registry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "dagSimulator")
    public DagSimulator dagSimulator(HarnessSimulationProperties properties,
                                     SimulationExecutorRegistry registry,
                                     SimulationLoopDriver loopDriver,
                                     SimulationConditionEvaluator conditionEvaluator) {
        return new DagSimulator(properties, registry, loopDriver, conditionEvaluator);
    }

    @Bean
    @ConditionalOnMissingBean(name = "stateMachineSimulator")
    public StateMachineSimulator stateMachineSimulator(HarnessSimulationProperties properties,
                                                       SimulationExecutorRegistry registry,
                                                       SimulationConditionEvaluator conditionEvaluator) {
        return new StateMachineSimulator(properties, registry, conditionEvaluator);
    }

    @Bean
    @ConditionalOnMissingBean
    public SimulationInputValidator simulationInputValidator(ObjectProvider<ObjectMapper> objectMapper,
                                                             HarnessSimulationProperties properties) {
        return new SimulationInputValidator(objectMapper.getIfAvailable(ObjectMapper::new), properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DraftSimulator draftSimulator(ObjectProvider<EngineSimulator> simulators,
                                         SimulationInputValidator inputValidator) {
        return new DraftSimulator(simulators.orderedStream().toList(), inputValidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public DraftConfirmationStore draftConfirmationStore(AiHarnessConfirmationMapper mapper) {
        return new DatabaseDraftConfirmationStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HarnessOperationStore harnessOperationStore(AiHarnessConfirmationMapper confirmationMapper,
                                                        AiHarnessOperationMapper operationMapper,
                                                        ObjectProvider<ObjectMapper> objectMapper) {
        return new DatabaseHarnessOperationStore(
                confirmationMapper, operationMapper, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean(name = "harnessRealRunExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "harnessRealRunExecutor")
    public ExecutorService harnessRealRunExecutor(HarnessRealRunProperties properties) {
        return Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrentOperations()),
                namedThreadFactory("ai-harness-real-run-"));
    }

    @Bean(name = "harnessRealRunHeartbeatExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "harnessRealRunHeartbeatExecutor")
    public ScheduledExecutorService harnessRealRunHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(namedThreadFactory("ai-harness-real-run-heartbeat-"));
    }

    @Bean
    @ConditionalOnMissingBean
    public DraftApplicationService draftApplicationService(DraftStore store,
                                                           FlowDefinitionCodec codec,
                                                           DraftNodeConverter converter,
                                                           DraftFieldValidator validator,
                                                           DraftValidator fullValidator,
                                                           DraftSimulator simulator,
                                                           ObjectProvider<DraftCommitter> committer,
                                                           ConditionCompiler conditionCompiler,
                                                           HarnessDraftProperties properties,
                                                           HarnessCommitProperties commitProperties,
                                                           ApplicationEventPublisher eventPublisher) {
        return new DraftApplicationService(store, codec, converter, validator, fullValidator, simulator,
                committer.getIfAvailable(), conditionCompiler, properties, commitProperties, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public CreateDraftToolDefinition createDraftToolDefinition(DraftApplicationService service) {
        return new CreateDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public UpdateDraftMetadataToolDefinition updateDraftMetadataToolDefinition(DraftApplicationService service) {
        return new UpdateDraftMetadataToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public AddNodeToolDefinition addNodeToolDefinition(DraftApplicationService service) {
        return new AddNodeToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public UpdateNodeToolDefinition updateNodeToolDefinition(DraftApplicationService service) {
        return new UpdateNodeToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public RemoveNodeToolDefinition removeNodeToolDefinition(DraftApplicationService service) {
        return new RemoveNodeToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectToolDefinition connectToolDefinition(DraftApplicationService service) {
        return new ConnectToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public DisconnectToolDefinition disconnectToolDefinition(DraftApplicationService service) {
        return new DisconnectToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReadDraftToolDefinition readDraftToolDefinition(DraftApplicationService service) {
        return new ReadDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public ValidateDraftToolDefinition validateDraftToolDefinition(DraftApplicationService service) {
        return new ValidateDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public SimulateDraftToolDefinition simulateDraftToolDefinition(DraftApplicationService service) {
        return new SimulateDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(DraftCommitter.class)
    @ConditionalOnMissingBean
    public CommitDraftToolDefinition commitDraftToolDefinition(DraftApplicationService service) {
        return new CommitDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public HarnessToolAuthorizer harnessToolAuthorizer() {
        return (tool, context) -> context != null && context.authenticated();
    }

    @Bean(name = "harnessToolExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "harnessToolExecutor")
    public ExecutorService harnessToolExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "ai-harness-tool-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newCachedThreadPool(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public HarnessToolScheduler harnessToolScheduler(ToolRegistry toolRegistry,
                                                     AiService aiService,
                                                     ObjectProvider<ObjectMapper> objectMapper,
                                                     HarnessToolAuthorizer authorizer,
                                                     @Qualifier("harnessToolExecutor") ExecutorService harnessToolExecutor,
                                                     AiProperties aiProperties) {
        int timeoutMs = aiProperties == null ? 10000 : aiProperties.getToolCalling().getToolTimeoutMs();
        return new HarnessToolScheduler(toolRegistry, aiService,
                objectMapper.getIfAvailable(ObjectMapper::new), authorizer, harnessToolExecutor, timeoutMs);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGenerationHarness flowGenerationHarness(
            AiService aiService,
            HarnessToolScheduler toolScheduler,
            ObjectProvider<HarnessPromptProvider> promptProviders,
            ObjectProvider<HarnessExampleProvider> exampleProviders,
            AiProperties aiProperties) {
        int maxIterations = aiProperties == null ? 5 : aiProperties.getToolCalling().getMaxIterations();
        return new FlowGenerationHarness(aiService, toolScheduler,
                promptProviders.orderedStream().toList(),
                exampleProviders.orderedStream().toList(),
                maxIterations);
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
