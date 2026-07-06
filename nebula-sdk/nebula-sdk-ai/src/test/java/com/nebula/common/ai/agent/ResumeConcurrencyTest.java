package com.nebula.common.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挂起实例唤醒的并发控制单测（决策 v3，阶段 3）：验证 {@link AgentInstanceStore#acquireForResume} 的 CAS 语义——
 * 对同一 SUSPENDED 实例并发抢占，只有<b>一次</b>能把 status 置 RUNNING（lock_version+1），其余因 lock_version
 * 已变影响 0 行被拒；非 SUSPENDED 态直接拒。绝不并发跑同一实例（文档 5.3）。
 *
 * @author nebula
 */
class ResumeConcurrencyTest {

    private AgentDefinition agent() {
        return new AgentDefinition().setAgentCode("cas").setFlowCode("f").setFlowVersion(1).setVersion(1);
    }

    private String suspendedInstance(InMemoryAgentInstanceStore store) {
        String id = store.create(agent(), "{}", "u1", "c1", Map.of(), null, null);
        // 落挂起态（lock_version 仍为 0）
        store.markSuspended(id, "review", Set.of("APPROVE"), Map.of("draft", "x"));
        return id;
    }

    @Test
    void onlyOneAcquireWinsOnSuspendedInstance() {
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        String id = suspendedInstance(store);

        // 两个 signal 都以 lock_version=0 抢占：第一次成功（转 RUNNING、lock_version→1），第二次被拒
        boolean first = store.acquireForResume(id, 0);
        boolean second = store.acquireForResume(id, 0);

        assertTrue(first, "首个抢占应成功");
        assertFalse(second, "已被抢先，第二个应被拒");
        assertEquals("RUNNING", store.peek(id).status());
        assertEquals(1, store.peek(id).lockVersion());
    }

    @Test
    void acquireRejectedWhenNotSuspended() {
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        String id = store.create(agent(), "{}", "u1", "c1", Map.of(), null, null);
        // 实例是 RUNNING（非挂起）→ 抢占应被拒
        assertFalse(store.acquireForResume(id, 0));

        // 终态实例也不可抢占
        store.markTerminal(id, "output", Map.of());
        assertFalse(store.acquireForResume(id, 0));
    }

    @Test
    void acquireRejectedOnStaleLockVersion() {
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        String id = suspendedInstance(store);
        // 期望 lock_version=5 但实际是 0 → 不匹配，抢占失败
        assertFalse(store.acquireForResume(id, 5));
        assertEquals("SUSPENDED", store.peek(id).status());
    }

    @Test
    void awaitingEventsPersistedOnSuspend() {
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        String id = suspendedInstance(store);
        AgentInstanceSnapshot snapshot = store.load(id);
        List<String> awaiting = snapshot.awaitingEvents();
        assertTrue(awaiting.contains("APPROVE"));
        assertEquals(0, snapshot.lockVersion());
        assertEquals("SUSPENDED", snapshot.status());
    }
}
