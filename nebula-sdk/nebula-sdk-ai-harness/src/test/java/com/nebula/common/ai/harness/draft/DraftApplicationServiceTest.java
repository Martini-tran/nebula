package com.nebula.common.ai.harness.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.CondGroupCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.flow.ModelProfile;
import com.nebula.common.ai.flow.ModelProfileRepository;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.config.HarnessDraftProperties;
import com.nebula.common.ai.harness.config.HarnessCommitProperties;
import com.nebula.common.ai.harness.config.HarnessSimulationProperties;
import com.nebula.common.ai.harness.simulate.DagSimulator;
import com.nebula.common.ai.harness.simulate.DraftSimulator;
import com.nebula.common.ai.harness.simulate.PlaceholderSimulationNodeExecutor;
import com.nebula.common.ai.harness.simulate.SimulationConditionEvaluator;
import com.nebula.common.ai.harness.simulate.SimulationExecutorRegistry;
import com.nebula.common.ai.harness.simulate.SimulationInputValidator;
import com.nebula.common.ai.harness.simulate.SimulationLoopDriver;
import com.nebula.common.ai.harness.simulate.StateMachineSimulator;
import com.nebula.common.ai.harness.simulate.StructuralSimulationNodeExecutor;
import com.nebula.common.ai.harness.validate.CommonRules;
import com.nebula.common.ai.harness.validate.DagRuleSet;
import com.nebula.common.ai.harness.validate.DraftValidator;
import com.nebula.common.ai.harness.validate.StateMachineRuleSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftApplicationServiceTest {

    private FlowDefinitionCodec codec;
    private InMemoryDraftStore store;
    private DraftApplicationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        codec = new FlowDefinitionCodec(objectMapper);
        store = new InMemoryDraftStore(codec);
        HarnessDraftProperties properties = new HarnessDraftProperties();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("toolRegistry", new ToolRegistry(List.of()));
        List<FlowNodeExecutor> executors = List.of(
                executor("START"), executor("END"), executor("PROMPT"));
        for (int i = 0; i < executors.size(); i++) {
            beans.addBean("executor" + i, executors.get(i));
        }
        // 已知档案之外一律返回 null：用于验证模型臆造的 profileCode 会被挡下
        Set<String> knownProfiles = Set.of("DS-V3-001", "default-profile");
        beans.addBean("modelProfileRepository", (ModelProfileRepository) profileCode ->
                knownProfiles.contains(profileCode)
                        ? new ModelProfile().setProfileCode(profileCode)
                                .setProvider("deepseek").setModel("deepseek-chat")
                        : null);
        DraftFieldValidator validator = new DraftFieldValidator(
                beans.getBeanProvider(ToolRegistry.class),
                beans.getBeanProvider(AgentDefinitionRepository.class),
                beans.getBeanProvider(FlowNodeExecutor.class),
                beans.getBeanProvider(ModelProfileRepository.class),
                codec,
                properties);
        ConditionCompiler conditionCompiler = new ConditionCompiler();
        DraftValidator fullValidator = new DraftValidator(
                validator,
                new CommonRules(),
                List.of(new DagRuleSet(), new StateMachineRuleSet()),
                new FlowGraphFactory(executors, conditionCompiler),
                new FlowStateMachineFactory(executors, conditionCompiler),
                conditionCompiler,
                new CondGroupCompiler(),
                codec);
        HarnessSimulationProperties simulationProperties = new HarnessSimulationProperties();
        SimulationExecutorRegistry simulationRegistry = new SimulationExecutorRegistry(List.of(
                new StructuralSimulationNodeExecutor(), new PlaceholderSimulationNodeExecutor()));
        SimulationConditionEvaluator conditionEvaluator = new SimulationConditionEvaluator(conditionCompiler);
        SimulationLoopDriver loopDriver = new SimulationLoopDriver(simulationProperties, simulationRegistry);
        DraftSimulator simulator = new DraftSimulator(List.of(
                new DagSimulator(simulationProperties, simulationRegistry, loopDriver, conditionEvaluator),
                new StateMachineSimulator(simulationProperties, simulationRegistry, conditionEvaluator)),
                new SimulationInputValidator(objectMapper, simulationProperties));
        service = new DraftApplicationService(
                store,
                codec,
                new DraftNodeConverter(objectMapper),
                validator,
                fullValidator,
                simulator,
                request -> store.markCommitted(request)
                        ? DraftCommitResult.success(request.definition().getFlowCode(), 1)
                        : DraftCommitResult.failure("DRAFT_CONFLICT", "草稿已变化", "重新读取草稿"),
                conditionCompiler,
                properties,
                new HarnessCommitProperties(),
                event -> { });
    }

    @Test
    void enforcesUserAndConditionalSessionBoundary() {
        DraftAccess owner = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(owner, "DAG", null, "demo", null);

        assertFalse(service.read(new DraftAccess(11L, "session-a"), created.draftId(), null, null, null).ok());
        assertFalse(service.read(new DraftAccess(10L, "session-b"), created.draftId(), null, null, null).ok());
        assertFalse(service.read(new DraftAccess(10L, null), created.draftId(), null, null, null).ok());
        assertTrue(service.read(owner, created.draftId(), null, null, null).ok());

        DraftOperationResult unbound = service.create(new DraftAccess(10L, null), "DAG", null, null, null);
        assertTrue(service.read(new DraftAccess(10L, "another-session"), unbound.draftId(), null, null, null).ok());
    }

    @Test
    void allowsEmptyFlowCodeAndRejectsStaleRevision() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", null, "demo", null);
        assertTrue(created.ok());
        assertNull(store.findOwned(created.draftId(), 10L).getFlowCode());

        DraftOperationResult first = service.updateMetadata(access, created.draftId(), 0,
                Map.of("flowCode", "demo-flow"), List.of());
        DraftOperationResult stale = service.updateMetadata(access, created.draftId(), 0,
                Map.of("name", "stale"), List.of());

        assertTrue(first.ok());
        assertFalse(stale.ok());
        assertEquals("DRAFT_CONFLICT", stale.issues().getFirst().code());
        FlowDraft persisted = store.findOwned(created.draftId(), 10L);
        assertEquals(1, persisted.getRevision());
        assertEquals("demo-flow", persisted.getFlowCode());
        assertEquals("demo", persisted.getName());
    }

    @Test
    void mapsFlatStateFieldsAndDoesNotPersistInvalidMutation() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "STATE_MACHINE", null, "state-demo", null);

        DraftOperationResult invalid = service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "review",
                "nodeType", "PROMPT",
                "promptTemplate", "review",
                "stateType", "BROKEN"));
        assertFalse(invalid.ok());
        assertEquals(0, store.findOwned(created.draftId(), 10L).getRevision());
        assertTrue(store.findOwned(created.draftId(), 10L).getGraph().getNodes().isEmpty());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nodeCode", "review");
        fields.put("nodeType", "PROMPT");
        fields.put("promptTemplate", "review");
        fields.put("outputKey", "reviewResult");
        fields.put("stateType", "ENTRY");
        fields.put("maxAttempts", 3);
        fields.put("backoffMs", 100L);
        fields.put("retryOn", List.of("TIMEOUT"));
        fields.put("stateTimeoutMs", 5000L);
        fields.put("onError", "GOTO_STATE");
        fields.put("errorState", "failed");
        fields.put("suspend", true);
        fields.put("awaitingEvents", List.of("APPROVED"));
        DraftOperationResult added = service.addNode(access, created.draftId(), 0, fields);

        assertTrue(added.ok());
        FlowNodeDefinition node = store.findOwned(created.draftId(), 10L).getGraph().getNodes().getFirst();
        assertEquals("ENTRY", node.getStateType());
        Map<?, ?> stateConfig = (Map<?, ?>) node.getNodeConfig().get("stateConfig");
        Map<?, ?> retry = (Map<?, ?>) stateConfig.get("retry");
        assertEquals(3, retry.get("maxAttempts"));
        assertEquals(100L, ((Number) retry.get("backoffMs")).longValue());
        assertEquals(5000L, ((Number) stateConfig.get("timeoutMs")).longValue());
        assertEquals("GOTO_STATE", stateConfig.get("onError"));
        assertEquals("failed", stateConfig.get("errorState"));
    }

    @Test
    void ambiguousDisconnectDoesNotDeleteEdgesOrAdvanceRevision() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", null, "dag", null);
        service.addNode(access, created.draftId(), 0,
                Map.of("nodeCode", "start", "nodeType", "START"));
        service.addNode(access, created.draftId(), 1,
                Map.of("nodeCode", "end", "nodeType", "END"));
        service.connect(access, created.draftId(), 2, "start", "end", null, null, 0);
        service.connect(access, created.draftId(), 3, "start", "end", "true", null, 1);

        DraftOperationResult result = service.disconnect(access, created.draftId(), 4,
                "start", "end", null, null, false, null, false);

        assertFalse(result.ok());
        assertEquals("AMBIGUOUS_EDGE", result.issues().getFirst().code());
        FlowDraft persisted = store.findOwned(created.draftId(), 10L);
        assertEquals(4, persisted.getRevision());
        assertEquals(2, persisted.getGraph().getEdges().size());
    }

    @Test
    void validationMarksOnlyErrorFreeRevisionAndCommitMakesDraftImmutable() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "validated-flow", "validated", null);
        service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "start", "nodeType", "START",
                "nodeConfig", Map.of("inputs", Map.of("topic", ""))));
        service.addNode(access, created.draftId(), 1, Map.of(
                "nodeCode", "write", "nodeType", "PROMPT",
                "promptTemplate", "围绕 #{topic} 写作", "outputKey", "article"));

        DraftOperationResult invalid = service.validate(access, created.draftId(), 2);
        assertFalse(invalid.ok());
        assertTrue(invalid.issues().stream().anyMatch(issue -> "INVALID_END_COUNT".equals(issue.code())));
        assertNull(store.findOwned(created.draftId(), 10L).getLastValidatedRevision());

        service.addNode(access, created.draftId(), 2,
                Map.of("nodeCode", "end", "nodeType", "END"));
        service.connect(access, created.draftId(), 3, "start", "write", null, null, 0);
        service.connect(access, created.draftId(), 4, "write", "end", null, null, 1);
        service.updateMetadata(access, created.draftId(), 5,
                Map.of("defaultProfileCode", "default-profile"), List.of());

        DraftOperationResult validated = service.validate(access, created.draftId(), 6);
        assertTrue(validated.ok());
        FlowDraft validatedDraft = store.findOwned(created.draftId(), 10L);
        assertEquals(6L, validatedDraft.getLastValidatedRevision());
        assertNull(validatedDraft.getGraph().getNodes().stream()
                .filter(node -> "write".equals(node.getNodeCode())).findFirst().orElseThrow().getProfileCode());

        DraftOperationResult notSimulated = service.commit(access, created.draftId(), 6);
        assertFalse(notSimulated.ok());
        assertEquals("DRAFT_NOT_SIMULATED", notSimulated.issues().getFirst().code());

        DraftOperationResult simulated = service.simulate(access, created.draftId(), 6, null);
        assertTrue(simulated.ok());
        assertEquals(6L, store.findOwned(created.draftId(), 10L).getLastSimulatedRevision());

        DraftOperationResult committed = service.commit(access, created.draftId(), 6);
        assertTrue(committed.ok());
        assertEquals("validated-flow", committed.payload().get("flowCode"));
        assertEquals(DraftStatus.COMMITTED, store.findOwned(created.draftId(), 10L).getStatus());

        DraftOperationResult retry = service.commit(access, created.draftId(), 6);
        assertFalse(retry.ok());
        assertEquals("DRAFT_IMMUTABLE", retry.issues().getFirst().code());
    }

    @Test
    void assumedStateMachineTerminalMarksRevisionAsSimulated() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "STATE_MACHINE",
                "assumed-state-flow", "assumed", null);
        service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "review", "nodeType", "PROMPT", "promptTemplate", "review",
                "outputKey", "reviewResult", "stateType", "ENTRY"));
        service.addNode(access, created.draftId(), 1, Map.of(
                "nodeCode", "published", "nodeType", "PROMPT", "promptTemplate", "published",
                "stateType", "TERMINAL"));
        service.connect(access, created.draftId(), 2, "review", "published",
                "getString('decision') == 'yes'", null, 0);
        service.updateMetadata(access, created.draftId(), 3,
                Map.of("defaultProfileCode", "default-profile"), List.of());

        DraftOperationResult result = service.simulate(access, created.draftId(), 4, Map.of());

        assertTrue(result.ok());
        Map<?, ?> simulation = (Map<?, ?>) result.payload().get("simulation");
        assertEquals("ASSUMED", simulation.get("confidence"));
        assertEquals(true, simulation.get("reachedTerminal"));
        assertTrue(result.issues().stream().anyMatch(issue -> "SIMULATION_GUARD_ASSUMED".equals(issue.code())));
        assertEquals(4L, store.findOwned(created.draftId(), 10L).getLastSimulatedRevision());
    }

    @Test
    void derivesOutputKeyFromNodeCodeForProducerNodes() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "derive-flow", "derive", null);

        service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "summarize", "nodeType", "PROMPT", "promptTemplate", "总结"));

        FlowNodeDefinition node = store.findOwned(created.draftId(), 10L).getGraph().getNodes().stream()
                .filter(item -> "summarize".equals(item.getNodeCode())).findFirst().orElseThrow();
        // 未显式声明 outputKey 时派生为 nodeCode，使校验/模拟与运行时写回规则一致
        assertEquals("summarize", node.getOutputKey());
    }

    @Test
    void keepsExplicitOutputKeyAndSkipsStructuralNodes() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "keep-flow", "keep", null);

        service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "writer", "nodeType", "PROMPT",
                "promptTemplate", "写作", "outputKey", "article"));
        service.addNode(access, created.draftId(), 1, Map.of("nodeCode", "start", "nodeType", "START"));

        List<FlowNodeDefinition> nodes = store.findOwned(created.draftId(), 10L).getGraph().getNodes();
        assertEquals("article", nodes.stream().filter(n -> "writer".equals(n.getNodeCode()))
                .findFirst().orElseThrow().getOutputKey());
        // START 不是产物节点，不应被塞 outputKey
        assertNull(nodes.stream().filter(n -> "start".equals(n.getNodeCode()))
                .findFirst().orElseThrow().getOutputKey());
    }

    @Test
    void rejectsAgentReactNodeWithoutPromptTemplate() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "react-flow", "react", null);

        DraftOperationResult result = service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "agent", "nodeType", "AGENT_REACT",
                "nodeConfig", Map.of("toolCodes", List.of("echo"))));

        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(issue ->
                "MISSING_REQUIRED_FIELD".equals(issue.code()) && "promptTemplate".equals(issue.field())));
    }

    @Test
    void inspectContextReportsGuaranteedAndConditionalVariables() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "inspect-flow", "inspect", null);
        long revision = 0;
        service.addNode(access, created.draftId(), revision++, Map.of(
                "nodeCode", "start", "nodeType", "START",
                "nodeConfig", Map.of("inputs", Map.of("topic", Map.of("type", "string")))));
        service.addNode(access, created.draftId(), revision++, Map.of(
                "nodeCode", "outline", "nodeType", "PROMPT",
                "promptTemplate", "为 {{topic}} 列大纲", "outputKey", "outline"));
        service.addNode(access, created.draftId(), revision++, Map.of(
                "nodeCode", "branch", "nodeType", "PROMPT",
                "promptTemplate", "分支", "outputKey", "branchOut"));
        service.addNode(access, created.draftId(), revision++, Map.of(
                "nodeCode", "writer", "nodeType", "PROMPT",
                "promptTemplate", "依据 {{outline}} 写作", "outputKey", "article"));
        service.connect(access, created.draftId(), revision++, "start", "outline", null, null, 0);
        service.connect(access, created.draftId(), revision++, "outline", "writer", null, null, 0);
        // branch 只在旁支上产出，不支配 writer
        service.connect(access, created.draftId(), revision++, "start", "branch", null, null, 1);
        service.connect(access, created.draftId(), revision, "branch", "writer", null, null, 1);

        DraftOperationResult result = service.inspectContext(access, created.draftId(), "writer");

        assertTrue(result.ok());
        assertEquals(List.of("topic"), result.payload().get("startInputs"));
        // 菱形结构：outline 与 branch 各在一条分支上，都不支配 writer，因此都只能是 conditional
        assertTrue(variablesOf(result, "conditional").contains("outline"));
        assertTrue(variablesOf(result, "conditional").contains("branchOut"));
        assertTrue(variablesOf(result, "guaranteed").isEmpty());
        // 已引用变量回显，便于模型自查笔误
        assertEquals(List.of("outline"), result.payload().get("referenced"));
    }

    @Test
    void inspectContextMarksLinearUpstreamAsGuaranteed() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "linear-flow", "linear", null);
        long revision = 0;
        service.addNode(access, created.draftId(), revision++, Map.of(
                "nodeCode", "start", "nodeType", "START"));
        service.addNode(access, created.draftId(), revision++, Map.of(
                "nodeCode", "outline", "nodeType", "PROMPT",
                "promptTemplate", "列大纲", "outputKey", "outline"));
        service.addNode(access, created.draftId(), revision++, Map.of(
                "nodeCode", "writer", "nodeType", "PROMPT", "promptTemplate", "写作"));
        service.connect(access, created.draftId(), revision++, "start", "outline", null, null, 0);
        service.connect(access, created.draftId(), revision, "outline", "writer", null, null, 0);

        DraftOperationResult result = service.inspectContext(access, created.draftId(), "writer");

        assertTrue(result.ok());
        // 唯一路径必经 outline，引用绝对安全
        assertTrue(variablesOf(result, "guaranteed").contains("outline"));
        assertTrue(variablesOf(result, "conditional").isEmpty());
    }

    @Test
    void rejectsFabricatedProfileCodeAndOutOfRangeSampling() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "profile-flow", "profile", null);

        DraftOperationResult fabricated = service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "writer", "nodeType", "PROMPT",
                "promptTemplate", "写作", "profileCode", "gpt-4"));
        assertFalse(fabricated.ok());
        assertTrue(fabricated.issues().stream().anyMatch(issue ->
                "INVALID_PROFILE_CODE".equals(issue.code()) && "profileCode".equals(issue.field())));

        DraftOperationResult outOfRange = service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "writer2", "nodeType", "PROMPT",
                "promptTemplate", "写作", "profileCode", "DS-V3-001", "temperature", 5));
        assertFalse(outOfRange.ok());
        assertTrue(outOfRange.issues().stream().anyMatch(issue ->
                "INVALID_FIELD_VALUE".equals(issue.code()) && "temperature".equals(issue.field())));

        // 合法档案 + 合法采样参数应当放行
        assertTrue(service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "ok", "nodeType", "PROMPT", "promptTemplate", "写作",
                "profileCode", "DS-V3-001", "temperature", 0.7, "maxTokens", 4096)).ok());
    }

    @Test
    void warnsWhenLlmNodeHasNoProfileAndWhenJsonModeLacksInstruction() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "warn-flow", "warn", null);

        // 节点与流程都没档案 → MISSING_MODEL_PROFILE 警告（不阻断）
        DraftOperationResult noProfile = service.addNode(access, created.draftId(), 0, Map.of(
                "nodeCode", "writer", "nodeType", "PROMPT", "promptTemplate", "写作"));
        assertTrue(noProfile.ok());
        assertTrue(noProfile.issues().stream().anyMatch(issue -> "MISSING_MODEL_PROFILE".equals(issue.code())));

        // outputMode=JSON 但提示词没提 JSON → JSON_MODE_WITHOUT_INSTRUCTION 警告
        DraftOperationResult jsonMode = service.addNode(access, created.draftId(), 1, Map.of(
                "nodeCode", "extract", "nodeType", "PROMPT",
                "promptTemplate", "抽取要点", "outputMode", "JSON", "profileCode", "DS-V3-001"));
        assertTrue(jsonMode.ok());
        assertTrue(jsonMode.issues().stream().anyMatch(issue ->
                "JSON_MODE_WITHOUT_INSTRUCTION".equals(issue.code())));

        // 提示词明确要求 JSON 后该节点不再告警
        // 注意：校验遍历全图，上面的 extract 节点仍会告警，故断言要按 nodeCode 收敛到本节点
        DraftOperationResult withInstruction = service.addNode(access, created.draftId(), 2, Map.of(
                "nodeCode", "extract2", "nodeType", "PROMPT",
                "promptTemplate", "抽取要点，只输出 JSON，字段为 title 和 tags",
                "outputMode", "JSON", "profileCode", "DS-V3-001"));
        assertTrue(withInstruction.issues().stream().noneMatch(issue ->
                "JSON_MODE_WITHOUT_INSTRUCTION".equals(issue.code())
                        && "extract2".equals(issue.nodeCode())));
    }

    @Test
    void inspectContextReportsEffectiveModelConfig() {
        DraftAccess access = new DraftAccess(10L, "session-a");
        DraftOperationResult created = service.create(access, "DAG", "cfg-flow", "cfg", null);
        service.updateMetadata(access, created.draftId(), 0,
                Map.of("defaultProfileCode", "DS-V3-001"), List.of());
        service.addNode(access, created.draftId(), 1, Map.of(
                "nodeCode", "writer", "nodeType", "PROMPT", "promptTemplate", "写作", "temperature", 0.9));

        DraftOperationResult result = service.inspectContext(access, created.draftId(), "writer");

        assertTrue(result.ok());
        Map<?, ?> modelConfig = (Map<?, ?>) result.payload().get("modelConfig");
        // 节点没设 profileCode，应回显继承自流程默认档案
        assertEquals("DS-V3-001", modelConfig.get("profileCode"));
        assertEquals("flowDefault", modelConfig.get("profileSource"));
        assertEquals(0.9, modelConfig.get("temperature"));
        assertEquals(false, modelConfig.get("hasSystemPrompt"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> variablesOf(DraftOperationResult result, String bucket) {
        return ((List<Map<String, Object>>) result.payload().get(bucket)).stream()
                .map(entry -> String.valueOf(entry.get("variable")))
                .toList();
    }

    private static FlowNodeExecutor executor(String type) {
        return new FlowNodeExecutor() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
                // 编译校验只需确认执行器存在，不执行节点。
            }
        };
    }

    private static final class InMemoryDraftStore implements DraftStore {

        private final Map<String, FlowDraft> drafts = new ConcurrentHashMap<>();
        private final FlowDefinitionCodec codec;

        private InMemoryDraftStore(FlowDefinitionCodec codec) {
            this.codec = codec;
        }

        @Override
        public synchronized FlowDraft create(FlowDraft draft) {
            drafts.put(draft.getDraftId(), copy(draft));
            return copy(draft);
        }

        @Override
        public synchronized FlowDraft findOwned(String draftId, Long userId) {
            FlowDraft draft = drafts.get(draftId);
            return draft == null || !draft.getUserId().equals(userId) ? null : copy(draft);
        }

        @Override
        public synchronized boolean compareAndSet(FlowDraft draft, long expectedRevision) {
            FlowDraft current = drafts.get(draft.getDraftId());
            if (current == null || current.getRevision() != expectedRevision
                    || current.getStatus() != DraftStatus.BUILDING
                    || !current.getUserId().equals(draft.getUserId())) {
                return false;
            }
            FlowDraft stored = copy(draft);
            stored.setRevision(expectedRevision + 1);
            drafts.put(stored.getDraftId(), stored);
            return true;
        }

        @Override
        public synchronized boolean markValidated(String draftId, Long userId, long expectedRevision) {
            FlowDraft current = drafts.get(draftId);
            if (current == null || !current.getUserId().equals(userId)
                    || current.getRevision() != expectedRevision
                    || current.getStatus() != DraftStatus.BUILDING) {
                return false;
            }
            current.setLastValidatedRevision(expectedRevision);
            return true;
        }

        @Override
        public synchronized boolean markSimulated(String draftId, Long userId, long expectedRevision) {
            FlowDraft current = drafts.get(draftId);
            if (current == null || !current.getUserId().equals(userId)
                    || current.getRevision() != expectedRevision
                    || current.getStatus() != DraftStatus.BUILDING) {
                return false;
            }
            current.setLastValidatedRevision(expectedRevision);
            current.setLastSimulatedRevision(expectedRevision);
            return true;
        }

        private synchronized boolean markCommitted(DraftCommitRequest request) {
            FlowDraft current = drafts.get(request.draftId());
            if (current == null || !current.getUserId().equals(request.userId())
                    || current.getRevision() != request.revision()
                    || !Long.valueOf(request.revision()).equals(current.getLastValidatedRevision())
                    || (request.requireSimulation()
                    && !Long.valueOf(request.revision()).equals(current.getLastSimulatedRevision()))
                    || current.getStatus() != DraftStatus.BUILDING) {
                return false;
            }
            current.setStatus(DraftStatus.COMMITTED);
            current.setCommittedFlowCode(request.definition().getFlowCode());
            return true;
        }

        private FlowDraft copy(FlowDraft source) {
            FlowDefinition graph = codec.copy(source.getGraph());
            return new FlowDraft()
                    .setDraftId(source.getDraftId())
                    .setSessionId(source.getSessionId())
                    .setUserId(source.getUserId())
                    .setFlowCode(source.getFlowCode())
                    .setName(source.getName())
                    .setDescription(source.getDescription())
                    .setEngineType(source.getEngineType())
                    .setRevision(source.getRevision())
                    .setLastValidatedRevision(source.getLastValidatedRevision())
                    .setLastSimulatedRevision(source.getLastSimulatedRevision())
                    .setStatus(source.getStatus())
                    .setCommittedFlowCode(source.getCommittedFlowCode())
                    .setCreateTime(source.getCreateTime())
                    .setUpdateTime(source.getUpdateTime())
                    .setGraph(graph);
        }
    }
}
