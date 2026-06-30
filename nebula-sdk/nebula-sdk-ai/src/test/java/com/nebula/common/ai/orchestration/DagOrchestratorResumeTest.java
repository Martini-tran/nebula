package com.nebula.common.ai.orchestration;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DagOrchestrator} 断点续跑单测：用内存 fake {@link RunStateStore} 驱动，覆盖
 * 正常落库、节点失败标 FAILED 并保留进度、按 runId 续跑跳过已完成节点、无 store 时纯内存回归、含条件边续跑。
 *
 * @author nebula
 */
class DagOrchestratorResumeTest {

    /**
     * 节点失败 → run 标 FAILED、已完成节点落库；同 runId 续跑 → 跳过 A、执行 B/C、最终 SUCCESS。
     */
    @Test
    void resumesFromBreakpointSkippingCompletedNodes() {
        InMemoryRunStateStore store = new InMemoryRunStateStore();
        DagOrchestrator orchestrator = new DagOrchestrator(store);

        AtomicInteger aRuns = new AtomicInteger();
        AtomicInteger bRuns = new AtomicInteger();
        AtomicInteger cRuns = new AtomicInteger();
        boolean[] bShouldFail = {true};

        OrchestrationGraph graph = OrchestrationGraph.builder("chain")
                .node("A", ctx -> {
                    aRuns.incrementAndGet();
                    ctx.put("a", "done-a");
                })
                .node("B", ctx -> {
                    bRuns.incrementAndGet();
                    if (bShouldFail[0]) {
                        throw new OrchestrationException("B 故意失败");
                    }
                    ctx.put("b", "done-b");
                })
                .node("C", ctx -> {
                    cRuns.incrementAndGet();
                    ctx.put("c", "done-c");
                })
                .edge("A", "B")
                .edge("B", "C")
                .build();

        String runId = store.startRun("chain", 1, "u1", "c1", Map.of("seed", 1));

        // 第一次执行：B 抛异常 → 向上传播
        OrchestrationContext ctx1 = new OrchestrationContext("u1", "c1");
        assertThrows(OrchestrationException.class,
                () -> orchestrator.run(graph, ctx1, new RunContext(runId, "chain", 1, false)));

        // 实例标 FAILED，A 已落库，B/C 未完成
        RunSnapshot afterFail = store.load(runId);
        assertEquals(RunStatus.FAILED, afterFail.status());
        assertEquals(Set.of("A"), afterFail.executedNodes());
        assertEquals("done-a", afterFail.attributes().get("a"));
        assertEquals(1, aRuns.get());
        assertEquals(1, bRuns.get());
        assertEquals(0, cRuns.get());

        // 修复后续跑：A 跳过、B/C 执行、SUCCESS
        bShouldFail[0] = false;
        OrchestrationContext ctx2 = new OrchestrationContext("u1", "c1");
        orchestrator.run(graph, ctx2, new RunContext(runId, "chain", 1, true));

        assertEquals(1, aRuns.get(), "A 续跑应被跳过，不重复执行");
        assertEquals(2, bRuns.get(), "B 续跑应重新执行");
        assertEquals(1, cRuns.get(), "C 续跑应执行");
        // 续跑上下文应恢复 A 的产物，并补齐 B/C
        assertEquals("done-a", ctx2.get("a"));
        assertEquals("done-b", ctx2.get("b"));
        assertEquals("done-c", ctx2.get("c"));

        RunSnapshot afterResume = store.load(runId);
        assertEquals(RunStatus.SUCCESS, afterResume.status());
        assertEquals(Set.of("A", "B", "C"), afterResume.executedNodes());
    }

    /**
     * 无 store（两参 run）：保持纯内存行为，不落库、不抛异常以外的副作用（回归保护）。
     */
    @Test
    void plainInMemoryRunUnaffectedWithoutStore() {
        DagOrchestrator orchestrator = new DagOrchestrator();
        AtomicInteger runs = new AtomicInteger();
        OrchestrationGraph graph = OrchestrationGraph.builder("t")
                .node("A", ctx -> {
                    runs.incrementAndGet();
                    ctx.put("a", 1);
                })
                .build();

        OrchestrationContext ctx = orchestrator.run(graph, new OrchestrationContext());
        assertEquals(1, runs.get());
        assertEquals(1, ctx.get("a"));
    }

    /**
     * 含条件边续跑：恢复 executed 后 isReachable 仍据已执行前驱 + 边条件正确路由。
     * A→B(cond: flag==1)、A→C(cond: flag==2)。首跑 flag=1 走 B 但 B 失败；续跑跳过 A、按 flag 仍走 B。
     */
    @Test
    void resumeRespectsConditionalEdges() {
        InMemoryRunStateStore store = new InMemoryRunStateStore();
        DagOrchestrator orchestrator = new DagOrchestrator(store);
        boolean[] bFail = {true};
        AtomicInteger cRuns = new AtomicInteger();

        OrchestrationGraph graph = OrchestrationGraph.builder("cond")
                .node("A", ctx -> ctx.put("flag", 1))
                .node("B", ctx -> {
                    if (bFail[0]) {
                        throw new OrchestrationException("B fail");
                    }
                    ctx.put("b", "ok");
                })
                .node("C", ctx -> {
                    cRuns.incrementAndGet();
                    ctx.put("c", "ok");
                })
                .edge("A", "B", ctx -> Integer.valueOf(1).equals(ctx.get("flag")))
                .edge("A", "C", ctx -> Integer.valueOf(2).equals(ctx.get("flag")))
                .build();

        String runId = store.startRun("cond", 1, null, null, null);
        OrchestrationContext ctx1 = new OrchestrationContext();
        assertThrows(OrchestrationException.class,
                () -> orchestrator.run(graph, ctx1, new RunContext(runId, "cond", 1, false)));
        assertEquals(Set.of("A"), store.load(runId).executedNodes());

        bFail[0] = false;
        OrchestrationContext ctx2 = new OrchestrationContext();
        orchestrator.run(graph, ctx2, new RunContext(runId, "cond", 1, true));

        assertEquals("ok", ctx2.get("b"), "续跑后条件边仍路由到 B");
        assertEquals(0, cRuns.get(), "flag=1 时 C 始终不可达");
        assertEquals(RunStatus.SUCCESS, store.load(runId).status());
    }

    /**
     * 续跑找不到实例快照时不应崩溃（按全新执行处理，全部节点执行）。
     */
    @Test
    void resumeWithMissingSnapshotRunsFresh() {
        InMemoryRunStateStore store = new InMemoryRunStateStore();
        DagOrchestrator orchestrator = new DagOrchestrator(store);
        AtomicInteger runs = new AtomicInteger();
        OrchestrationGraph graph = OrchestrationGraph.builder("t")
                .node("A", ctx -> runs.incrementAndGet())
                .build();

        // 不 startRun，直接以未知 runId resume
        OrchestrationContext ctx = new OrchestrationContext();
        orchestrator.run(graph, ctx, new RunContext("ghost-run", "t", 1, true));
        assertEquals(1, runs.get());
    }

    /**
     * 内存版 RunStateStore：跨 run 调用保持已执行集与产物快照，供续跑测试用。
     */
    private static final class InMemoryRunStateStore implements RunStateStore {

        private final Map<String, Record> store = new LinkedHashMap<>();

        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public String startRun(String flowCode, int version, String userId, String conversationId,
                               Map<String, Object> input) {
            String runId = flowCode + "-" + seq.incrementAndGet();
            Record r = new Record();
            r.flowCode = flowCode;
            r.version = version;
            r.userId = userId;
            r.conversationId = conversationId;
            r.status = RunStatus.RUNNING;
            store.put(runId, r);
            return runId;
        }

        @Override
        public RunSnapshot load(String runId) {
            Record r = store.get(runId);
            if (r == null) {
                return null;
            }
            return new RunSnapshot(runId, r.flowCode, r.version, r.status, r.userId, r.conversationId,
                    new LinkedHashSet<>(r.executed), new LinkedHashMap<>(r.attributes));
        }

        @Override
        public void saveNodeDone(String runId, String nodeCode, int s, Map<String, Object> attributesSnapshot) {
            Record r = store.get(runId);
            r.executed.add(nodeCode);
            r.attributes = new LinkedHashMap<>(attributesSnapshot);
            r.status = RunStatus.RUNNING;
        }

        @Override
        public void markFinished(String runId, Map<String, Object> attributesSnapshot) {
            Record r = store.get(runId);
            r.status = RunStatus.SUCCESS;
            r.attributes = new LinkedHashMap<>(attributesSnapshot);
        }

        @Override
        public void markFailed(String runId, String failedNodeCode, String error, Map<String, Object> snapshot) {
            Record r = store.get(runId);
            r.status = RunStatus.FAILED;
            r.attributes = new LinkedHashMap<>(snapshot);
        }

        private static final class Record {
            String flowCode;
            int version;
            String userId;
            String conversationId;
            RunStatus status;
            final Set<String> executed = new LinkedHashSet<>();
            Map<String, Object> attributes = new LinkedHashMap<>();
        }
    }
}
