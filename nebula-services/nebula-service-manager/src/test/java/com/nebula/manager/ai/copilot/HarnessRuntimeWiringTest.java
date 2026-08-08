package com.nebula.manager.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowEngine;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.NoOpNodeExecutor;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.flow.store.AiHarnessConfirmationMapper;
import com.nebula.common.ai.flow.store.AiHarnessOperationMapper;
import com.nebula.common.ai.harness.config.HarnessAutoConfiguration;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.config.HarnessRealRunAutoConfiguration;
import com.nebula.common.ai.harness.draft.DraftStore;
import com.nebula.common.ai.harness.draft.FlowDefinitionCodec;
import com.nebula.common.ai.harness.draft.FlowDraft;
import com.nebula.common.ai.harness.realrun.DatabaseDraftConfirmationStore;
import com.nebula.common.ai.harness.realrun.DatabaseHarnessOperationStore;
import com.nebula.common.ai.harness.realrun.DraftRealRunService;
import com.nebula.common.ai.harness.runtime.FlowGenerationHarness;
import com.nebula.common.ai.harness.simulate.SimulationInputValidator;
import com.nebula.common.ai.harness.tool.GetHarnessOperationToolDefinition;
import com.nebula.common.ai.harness.tool.RealRunDraftToolDefinition;
import com.nebula.common.ai.harness.validate.DraftValidator;
import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.manager.config.HarnessRealRunScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 B4 在真实 Spring Bean 生命周期中的装配结果。 */
class HarnessRuntimeWiringTest {

    @Test
    void b4组件和工具注册表完整装配() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            assertNotNull(context.getBean(ManagerDraftRunner.class));
            assertNotNull(context.getBean(DraftRealRunService.class));
            assertNotNull(context.getBean(RealRunDraftToolDefinition.class));
            assertNotNull(context.getBean(GetHarnessOperationToolDefinition.class));
            assertNotNull(context.getBean(HarnessRealRunScheduler.class));
            assertInstanceOf(DatabaseDraftConfirmationStore.class,
                    context.getBean(com.nebula.common.ai.harness.realrun.DraftConfirmationStore.class));
            assertInstanceOf(DatabaseHarnessOperationStore.class,
                    context.getBean(com.nebula.common.ai.harness.realrun.HarnessOperationStore.class));
            assertNotNull(context.getBean(FlowGenerationHarness.class));

            ToolRegistry registry = context.getBean(ToolRegistry.class);
            assertTrue(registry.contains("real_run_draft"));
            assertTrue(registry.contains("get_harness_operation"));
            assertFalse(registry.contains("generate_flow"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({HarnessAutoConfiguration.class, HarnessRealRunAutoConfiguration.class})
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AiProperties aiProperties() {
            return new AiProperties();
        }

        @Bean
        AiService aiService() {
            return new NoOpAiService();
        }

        @Bean
        ToolRegistry toolRegistry(ObjectProvider<ToolDefinition> definitions) {
            return new ToolRegistry(definitions.orderedStream().toList());
        }

        @Bean
        AiHarnessConfirmationMapper confirmationMapper() {
            return mapperProxy(AiHarnessConfirmationMapper.class);
        }

        @Bean
        AiHarnessOperationMapper operationMapper() {
            return mapperProxy(AiHarnessOperationMapper.class);
        }

        @Bean
        DraftStore draftStore() {
            return new DraftStore() {
                @Override
                public FlowDraft create(FlowDraft draft) {
                    return draft;
                }

                @Override
                public FlowDraft findOwned(String draftId, Long userId) {
                    return null;
                }

                @Override
                public boolean compareAndSet(FlowDraft draft, long expectedRevision) {
                    return false;
                }

                @Override
                public boolean markValidated(String draftId, Long userId, long expectedRevision) {
                    return false;
                }

                @Override
                public boolean markSimulated(String draftId, Long userId, long expectedRevision) {
                    return false;
                }
            };
        }

        @Bean
        FlowDefinitionCodec flowDefinitionCodec(ObjectMapper objectMapper) {
            return new FlowDefinitionCodec(objectMapper);
        }

        @Bean
        SimulationInputValidator simulationInputValidator(ObjectMapper objectMapper) {
            return new SimulationInputValidator(objectMapper, new HarnessSimulationProperties());
        }

        @Bean
        DraftValidator draftValidator(FlowDefinitionCodec codec) {
            // 装配测试不执行草稿；依赖只用于证明 DraftRealRunService 的容器构造链完整。
            return new DraftValidator(null, null, List.of(), null, null, null, null, codec);
        }

        @Bean
        FlowEngine flowEngine() {
            FlowGraphFactory graphFactory = new FlowGraphFactory(List.of(
                    new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_START),
                    new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_END)), new ConditionCompiler());
            return new FlowEngine(code -> null, graphFactory, new DagOrchestrator());
        }

        @Bean
        ManagerDraftRunner managerDraftRunner(FlowEngine flowEngine) {
            return new ManagerDraftRunner(flowEngine);
        }

        @Bean
        HarnessRealRunScheduler harnessRealRunScheduler(DraftRealRunService service) {
            return new HarnessRealRunScheduler(service);
        }

        @SuppressWarnings("unchecked")
        private static <T> T mapperProxy(Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            return 0;
        }
    }

    private static final class NoOpAiService implements AiService {
        @Override
        public Map<String, Object> chat(AiRequest request) {
            return Map.of("content", "");
        }

        @Override
        public CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
            return CompletableFuture.completedFuture(chat(request));
        }

        @Override
        public void stream(AiRequest request, AiCallback callback) {
        }

        @Override
        public void saveMessage(String conversationId, Map<String, Object> message) {
        }

        @Override
        public List<Map<String, Object>> listMessages(String conversationId) {
            return List.of();
        }

        @Override
        public void log(Map<String, Object> record) {
        }

        @Override
        public void clearConversation(String conversationId) {
        }
    }
}
