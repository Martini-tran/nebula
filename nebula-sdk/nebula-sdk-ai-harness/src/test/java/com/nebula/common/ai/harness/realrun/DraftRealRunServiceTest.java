package com.nebula.common.ai.harness.realrun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.flow.CondGroupCompiler;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.config.HarnessDraftProperties;
import com.nebula.common.ai.harness.config.HarnessRealRunProperties;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftFieldValidator;
import com.nebula.common.ai.harness.draft.DraftStatus;
import com.nebula.common.ai.harness.draft.DraftStore;
import com.nebula.common.ai.harness.draft.FlowDefinitionCodec;
import com.nebula.common.ai.harness.draft.FlowDraft;
import com.nebula.common.ai.harness.simulate.SimulationInputValidator;
import com.nebula.common.ai.harness.validate.CommonRules;
import com.nebula.common.ai.harness.validate.DagRuleSet;
import com.nebula.common.ai.harness.validate.DraftValidator;
import com.nebula.common.ai.harness.validate.StateMachineRuleSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftRealRunServiceTest {

    private ExecutorService operationExecutor;
    private ScheduledExecutorService heartbeatExecutor;
    private InMemoryGate gate;
    private AtomicInteger runs;
    private DraftRealRunService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        FlowDefinitionCodec codec = new FlowDefinitionCodec(objectMapper);
        FlowDraft draft = draft();
        DraftStore draftStore = new FixedDraftStore(draft, codec);
        gate = new InMemoryGate();
        runs = new AtomicInteger();
        operationExecutor = Executors.newFixedThreadPool(2);
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        HarnessRealRunProperties properties = new HarnessRealRunProperties();
        properties.setWaitTimeout(Duration.ofSeconds(2));
        properties.setHeartbeatInterval(Duration.ofMillis(100));
        service = new DraftRealRunService(
                draftStore,
                gate,
                gate,
                request -> {
                    runs.incrementAndGet();
                    return new DraftRunResult(Map.of(
                            "answer", "ok",
                            "apiKey", "must-not-leak",
                            "__flowCode", "temporary"));
                },
                validator(codec),
                new SimulationInputValidator(objectMapper, new HarnessSimulationProperties()),
                codec,
                objectMapper,
                properties,
                operationExecutor,
                heartbeatExecutor);
    }

    @AfterEach
    void tearDown() {
        operationExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    @Test
    void requiresConfirmationBindsInputAndStartsRevisionOnlyOnce() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        RealRunResult first = service.realRun(access, "draft-1", 3, Map.of("topic", "A"), null);

        assertFalse(first.ok());
        assertEquals("CONFIRM_REQUIRED", first.code());
        assertEquals(0, runs.get());

        String confirmationId = String.valueOf(first.payload().get("confirmationId"));
        RealRunResult confirmed = service.confirm(access, confirmationId);
        assertTrue(confirmed.ok());
        String token = String.valueOf(confirmed.payload().get("confirmationToken"));

        RealRunResult mismatched = service.realRun(access, "draft-1", 3, Map.of("topic", "B"), token);
        assertEquals("CONFIRMATION_INVALID", mismatched.code());
        assertEquals(0, runs.get());

        RealRunResult executed = service.realRun(access, "draft-1", 3, Map.of("topic", "A"), token);
        assertTrue(executed.ok());
        assertEquals("SUCCEEDED", executed.payload().get("status"));
        assertEquals(1, runs.get());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) executed.payload().get("result");
        assertEquals("[REDACTED]", result.get("apiKey"));
        assertFalse(result.containsKey("__flowCode"));

        RealRunResult repeated = service.realRun(access, "draft-1", 3, Map.of("topic", "B"), null);
        assertTrue(repeated.ok());
        assertEquals(executed.payload().get("operationId"), repeated.payload().get("operationId"));
        assertEquals(1, runs.get());
    }

    @Test
    void initialInput允许顶层JsonNull值() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("optional", null);

        RealRunResult first = service.realRun(access, "draft-1", 3, input, null);
        RealRunResult confirmed = service.confirm(
                access, String.valueOf(first.payload().get("confirmationId")));
        RealRunResult executed = service.realRun(
                access, "draft-1", 3, input,
                String.valueOf(confirmed.payload().get("confirmationToken")));

        assertTrue(executed.ok());
        assertEquals("SUCCEEDED", executed.payload().get("status"));
        assertEquals(1, runs.get());
    }

    @Test
    void 待执行操作只允许使用已授权输入补提交() {
        Map<String, Object> authorizedInput = Map.of("topic", "A");
        HarnessOperation existing = new HarnessOperation(
                "op-existing",
                DraftRealRunService.ACTION,
                "draft-1",
                3,
                10L,
                "session-a",
                new RealRunSecurity(new ObjectMapper()).inputDigest(authorizedInput),
                HarnessOperationStatus.PENDING,
                Map.of(),
                null,
                null,
                null,
                null,
                null);
        gate.operations.put(existing.operationId(), existing);

        RealRunResult result = service.realRun(
                new DraftAccess(10L, "session-a"),
                "draft-1",
                3,
                Map.of("topic", "B"),
                null);

        assertFalse(result.ok());
        assertEquals("OPERATION_INPUT_MISMATCH", result.code());
        assertEquals(0, runs.get());
        assertEquals(HarnessOperationStatus.PENDING,
                gate.operations.get(existing.operationId()).status());
    }

    private FlowDraft draft() {
        FlowDefinition definition = new FlowDefinition().setFlowCode("draft-flow").setEngineType("DAG");
        definition.getNodes().add(new FlowNodeDefinition()
                .setNodeCode("start").setNodeType("START").setSortNo(0));
        definition.getNodes().add(new FlowNodeDefinition()
                .setNodeCode("end").setNodeType("END").setSortNo(1));
        definition.getEdges().add(new FlowEdgeDefinition()
                .setFromNode("start").setToNode("end").setSortNo(0));
        return new FlowDraft()
                .setDraftId("draft-1")
                .setUserId(10L)
                .setSessionId("session-a")
                .setFlowCode("draft-flow")
                .setEngineType("DAG")
                .setRevision(3)
                .setLastValidatedRevision(3L)
                .setLastSimulatedRevision(3L)
                .setStatus(DraftStatus.BUILDING)
                .setGraph(definition);
    }

    private DraftValidator validator(FlowDefinitionCodec codec) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("toolRegistry", new ToolRegistry(List.of()));
        List<FlowNodeExecutor> executors = List.of(executor("START"), executor("END"));
        for (int i = 0; i < executors.size(); i++) {
            beans.addBean("executor" + i, executors.get(i));
        }
        DraftFieldValidator fieldValidator = new DraftFieldValidator(
                beans.getBeanProvider(ToolRegistry.class),
                beans.getBeanProvider(AgentDefinitionRepository.class),
                beans.getBeanProvider(FlowNodeExecutor.class),
                codec,
                new HarnessDraftProperties());
        ConditionCompiler conditions = new ConditionCompiler();
        return new DraftValidator(
                fieldValidator,
                new CommonRules(),
                List.of(new DagRuleSet(), new StateMachineRuleSet()),
                new FlowGraphFactory(executors, conditions),
                new FlowStateMachineFactory(executors, conditions),
                conditions,
                new CondGroupCompiler(),
                codec);
    }

    private FlowNodeExecutor executor(String type) {
        return new FlowNodeExecutor() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public void execute(FlowNodeDefinition node,
                                com.nebula.common.ai.orchestration.OrchestrationContext context) {
                // 构图测试不执行。
            }
        };
    }

    private static final class FixedDraftStore implements DraftStore {

        private final FlowDraft draft;
        private final FlowDefinitionCodec codec;

        private FixedDraftStore(FlowDraft draft, FlowDefinitionCodec codec) {
            this.draft = draft;
            this.codec = codec;
        }

        @Override
        public FlowDraft create(FlowDraft value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FlowDraft findOwned(String draftId, Long userId) {
            if (!draft.getDraftId().equals(draftId) || !draft.getUserId().equals(userId)) {
                return null;
            }
            return new FlowDraft()
                    .setDraftId(draft.getDraftId())
                    .setUserId(draft.getUserId())
                    .setSessionId(draft.getSessionId())
                    .setFlowCode(draft.getFlowCode())
                    .setEngineType(draft.getEngineType())
                    .setRevision(draft.getRevision())
                    .setLastValidatedRevision(draft.getLastValidatedRevision())
                    .setLastSimulatedRevision(draft.getLastSimulatedRevision())
                    .setStatus(draft.getStatus())
                    .setGraph(codec.copy(draft.getGraph()));
        }

        @Override
        public boolean compareAndSet(FlowDraft value, long expectedRevision) {
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
    }

    private static final class InMemoryGate implements DraftConfirmationStore, HarnessOperationStore {

        private final Map<String, DraftConfirmation> confirmations = new ConcurrentHashMap<>();
        private final Map<String, String> tokenHashes = new ConcurrentHashMap<>();
        private final Map<String, HarnessOperation> operations = new ConcurrentHashMap<>();

        @Override
        public DraftConfirmation createOrRefresh(DraftConfirmationRequest request) {
            DraftConfirmation value = new DraftConfirmation(
                    request.confirmationId(), request.action(), request.draftId(), request.draftRevision(),
                    request.userId(), request.sessionId(), request.inputDigest(), ConfirmationStatus.PENDING,
                    request.expiresAt(), null, null);
            confirmations.clear();
            confirmations.put(value.confirmationId(), value);
            return value;
        }

        @Override
        public DraftConfirmation findOwned(String confirmationId, Long userId) {
            DraftConfirmation value = confirmations.get(confirmationId);
            return value != null && value.userId().equals(userId) ? value : null;
        }

        @Override
        public boolean confirm(String confirmationId, Long userId, String tokenHash) {
            DraftConfirmation value = findOwned(confirmationId, userId);
            if (value == null || value.status() != ConfirmationStatus.PENDING) {
                return false;
            }
            tokenHashes.put(confirmationId, tokenHash);
            confirmations.put(confirmationId, new DraftConfirmation(
                    value.confirmationId(), value.action(), value.draftId(), value.draftRevision(), value.userId(),
                    value.sessionId(), value.inputDigest(), ConfirmationStatus.CONFIRMED, value.expiresAt(),
                    LocalDateTime.now(), null));
            return true;
        }

        @Override
        public int expireDue() {
            return 0;
        }

        @Override
        public int purgeTerminalBefore(LocalDateTime before) {
            return 0;
        }

        @Override
        public HarnessOperation findByKey(String action, String draftId, long revision, Long userId) {
            return operations.values().stream()
                    .filter(value -> value.action().equals(action)
                            && value.draftId().equals(draftId)
                            && value.draftRevision() == revision
                            && value.userId().equals(userId))
                    .findFirst().orElse(null);
        }

        @Override
        public HarnessOperation findOperationOwned(String operationId, Long userId) {
            HarnessOperation value = operations.get(operationId);
            return value != null && value.userId().equals(userId) ? value : null;
        }

        @Override
        public synchronized OperationAuthorization authorizeAndCreate(
                String tokenHash, HarnessOperationRequest request) {
            DraftConfirmation confirmation = confirmations.values().stream().findFirst().orElse(null);
            if (confirmation == null || confirmation.status() != ConfirmationStatus.CONFIRMED
                    || !tokenHash.equals(tokenHashes.get(confirmation.confirmationId()))
                    || !request.inputDigest().equals(confirmation.inputDigest())) {
                return OperationAuthorization.rejected();
            }
            HarnessOperation existing = findByKey(
                    request.action(), request.draftId(), request.draftRevision(), request.userId());
            if (existing != null) {
                return OperationAuthorization.authorized(existing);
            }
            HarnessOperation operation = operation(request, HarnessOperationStatus.PENDING, Map.of());
            operations.put(operation.operationId(), operation);
            return OperationAuthorization.authorized(operation);
        }

        @Override
        public synchronized boolean claim(String operationId) {
            HarnessOperation value = operations.get(operationId);
            if (value == null || value.status() != HarnessOperationStatus.PENDING) {
                return false;
            }
            operations.put(operationId, withStatus(value, HarnessOperationStatus.RUNNING, Map.of()));
            return true;
        }

        @Override
        public boolean heartbeat(String operationId) {
            return operations.containsKey(operationId);
        }

        @Override
        public synchronized boolean markSucceeded(String operationId, Map<String, Object> resultSummary) {
            HarnessOperation value = operations.get(operationId);
            operations.put(operationId, withStatus(value, HarnessOperationStatus.SUCCEEDED, resultSummary));
            return true;
        }

        @Override
        public synchronized boolean markFailed(String operationId, String errorCode, String errorMessage) {
            HarnessOperation value = operations.get(operationId);
            operations.put(operationId, new HarnessOperation(
                    value.operationId(), value.action(), value.draftId(), value.draftRevision(), value.userId(),
                    value.sessionId(), value.inputDigest(), HarnessOperationStatus.FAILED, Map.of(), errorCode,
                    errorMessage, value.startedAt(), value.heartbeatAt(), LocalDateTime.now()));
            return true;
        }

        @Override
        public int markUnknownStale(LocalDateTime staleBefore) {
            return 0;
        }

        @Override
        public int expireResultDetails(LocalDateTime before) {
            return 0;
        }

        private HarnessOperation operation(HarnessOperationRequest request,
                                           HarnessOperationStatus status,
                                           Map<String, Object> result) {
            return new HarnessOperation(
                    request.operationId(), request.action(), request.draftId(), request.draftRevision(),
                    request.userId(), request.sessionId(), request.inputDigest(), status, result,
                    null, null, null, null, null);
        }

        private HarnessOperation withStatus(HarnessOperation value,
                                            HarnessOperationStatus status,
                                            Map<String, Object> result) {
            return new HarnessOperation(
                    value.operationId(), value.action(), value.draftId(), value.draftRevision(), value.userId(),
                    value.sessionId(), value.inputDigest(), status, result, value.errorCode(), value.errorMessage(),
                    status == HarnessOperationStatus.RUNNING ? LocalDateTime.now() : value.startedAt(),
                    LocalDateTime.now(), status.terminal() ? LocalDateTime.now() : null);
        }
    }
}
