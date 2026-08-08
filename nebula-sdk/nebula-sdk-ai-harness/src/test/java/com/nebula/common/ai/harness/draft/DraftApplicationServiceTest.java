package com.nebula.common.ai.harness.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.harness.config.HarnessDraftProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        DraftFieldValidator validator = new DraftFieldValidator(
                beans.getBeanProvider(ToolRegistry.class),
                beans.getBeanProvider(AgentDefinitionRepository.class),
                beans.getBeanProvider(FlowNodeExecutor.class),
                codec,
                properties);
        service = new DraftApplicationService(
                store,
                codec,
                new DraftNodeConverter(objectMapper),
                validator,
                new ConditionCompiler(),
                properties,
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
