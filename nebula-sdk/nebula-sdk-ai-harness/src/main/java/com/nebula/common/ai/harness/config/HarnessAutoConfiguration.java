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
import com.nebula.common.ai.flow.store.AiFlowStoreAutoConfiguration;
import com.nebula.common.ai.harness.conversation.HarnessExampleProvider;
import com.nebula.common.ai.harness.conversation.HarnessPromptProvider;
import com.nebula.common.ai.harness.conversation.HarnessToolAuthorizer;
import com.nebula.common.ai.harness.runtime.FlowGenerationHarness;
import com.nebula.common.ai.harness.runtime.HarnessToolScheduler;
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
import com.nebula.common.ai.harness.tool.UpdateDraftMetadataToolDefinition;
import com.nebula.common.ai.harness.tool.UpdateNodeToolDefinition;
import com.nebula.common.ai.harness.tool.ValidateDraftToolDefinition;
import com.nebula.common.ai.harness.validate.CommonRules;
import com.nebula.common.ai.harness.validate.DagRuleSet;
import com.nebula.common.ai.harness.validate.DraftValidator;
import com.nebula.common.ai.harness.validate.EngineRuleSet;
import com.nebula.common.ai.harness.validate.StateMachineRuleSet;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流程生成 Harness 自动装配。
 *
 * @author nebula
 */
@AutoConfiguration(after = {FlowAutoConfiguration.class, AiFlowStoreAutoConfiguration.class})
@ConditionalOnBean({AiService.class, ToolRegistry.class})
@EnableConfigurationProperties(HarnessDraftProperties.class)
public class HarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ListNodeTypesToolDefinition listNodeTypesToolDefinition(
            ObjectProvider<FlowNodeExecutor> executorProvider) {
        return new ListNodeTypesToolDefinition(executorProvider);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public FlowDefinitionCodec flowDefinitionCodec(ObjectProvider<ObjectMapper> objectMapper) {
        return new FlowDefinitionCodec(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public DraftStore draftStore(AiFlowDraftMapper mapper, FlowDefinitionCodec codec) {
        return new DatabaseDraftStore(mapper, codec);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public DraftNodeConverter draftNodeConverter(ObjectProvider<ObjectMapper> objectMapper) {
        return new DraftNodeConverter(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public DraftFieldValidator draftFieldValidator(ObjectProvider<ToolRegistry> toolRegistry,
                                                   ObjectProvider<AgentDefinitionRepository> agentRepository,
                                                   ObjectProvider<FlowNodeExecutor> nodeExecutors,
                                                   FlowDefinitionCodec codec,
                                                   HarnessDraftProperties properties) {
        return new DraftFieldValidator(toolRegistry, agentRepository, nodeExecutors, codec, properties);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public CommonRules commonDraftRules() {
        return new CommonRules();
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean(name = "dagDraftRuleSet")
    public DagRuleSet dagDraftRuleSet() {
        return new DagRuleSet();
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean(name = "stateMachineDraftRuleSet")
    public StateMachineRuleSet stateMachineDraftRuleSet() {
        return new StateMachineRuleSet();
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
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
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public DraftApplicationService draftApplicationService(DraftStore store,
                                                           FlowDefinitionCodec codec,
                                                           DraftNodeConverter converter,
                                                           DraftFieldValidator validator,
                                                           DraftValidator fullValidator,
                                                           ObjectProvider<DraftCommitter> committer,
                                                           ConditionCompiler conditionCompiler,
                                                           HarnessDraftProperties properties,
                                                           ApplicationEventPublisher eventPublisher) {
        return new DraftApplicationService(store, codec, converter, validator, fullValidator,
                committer.getIfAvailable(), conditionCompiler, properties, eventPublisher);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public CreateDraftToolDefinition createDraftToolDefinition(DraftApplicationService service) {
        return new CreateDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public UpdateDraftMetadataToolDefinition updateDraftMetadataToolDefinition(DraftApplicationService service) {
        return new UpdateDraftMetadataToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public AddNodeToolDefinition addNodeToolDefinition(DraftApplicationService service) {
        return new AddNodeToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public UpdateNodeToolDefinition updateNodeToolDefinition(DraftApplicationService service) {
        return new UpdateNodeToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public RemoveNodeToolDefinition removeNodeToolDefinition(DraftApplicationService service) {
        return new RemoveNodeToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public ConnectToolDefinition connectToolDefinition(DraftApplicationService service) {
        return new ConnectToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public DisconnectToolDefinition disconnectToolDefinition(DraftApplicationService service) {
        return new DisconnectToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public ReadDraftToolDefinition readDraftToolDefinition(DraftApplicationService service) {
        return new ReadDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean(AiFlowDraftMapper.class)
    @ConditionalOnMissingBean
    public ValidateDraftToolDefinition validateDraftToolDefinition(DraftApplicationService service) {
        return new ValidateDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnBean({AiFlowDraftMapper.class, DraftCommitter.class})
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
}
