package com.nebula.common.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转移历史读取（{@link AgentInstanceStore#loadTransitions}，阶段 4）单测：验证返回按 seq 升序的完整轨迹
 * （含 RETRY 行），SUCCESS 行的 nodeResult delta 正确、RETRY 行 delta 为空（错误摘要不作产物），供回放时间线与增量重放使用。
 *
 * @author nebula
 */
class LoadTransitionsTest {

    private AgentDefinition agent() {
        return new AgentDefinition().setAgentCode("t").setFlowCode("f").setFlowVersion(1).setVersion(1);
    }

    @Test
    void loadTransitionsReturnsFullTrailInSeqOrder() {
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        String id = store.create(agent(), "{}", "u1", "c1", Map.of(), null, null);
        // 乱序落库：先 seq1 再 seq0，验证返回按 seq 升序
        store.appendSuccess(id, "b", 1, 0, Map.of("b_out", "B"));
        store.appendRetry(id, "a", 0, 0, "TOOL_ERROR: 抖动");
        store.appendSuccess(id, "a", 0, 1, Map.of("a_out", "A"));

        List<TransitionRecord> records = store.loadTransitions(id);

        // 按 seq 升序（seq0 的两行 + seq1）
        assertEquals(3, records.size());
        assertTrue(records.get(0).seq() <= records.get(1).seq());
        assertTrue(records.get(1).seq() <= records.get(2).seq());

        // SUCCESS 行 delta 正确
        TransitionRecord aSuccess = records.stream()
                .filter(r -> "a".equals(r.toState()) && r.isSuccess()).findFirst().orElseThrow();
        assertEquals("A", aSuccess.nodeResult().get("a_out"));

        // RETRY 行 delta 为空（错误摘要不作产物）
        TransitionRecord aRetry = records.stream()
                .filter(r -> "a".equals(r.toState()) && "RETRY".equals(r.outcome())).findFirst().orElseThrow();
        assertTrue(aRetry.nodeResult().isEmpty(), "RETRY 行 nodeResult 应为空 Map");
    }

    @Test
    void loadTransitionsOfMissingInstanceIsEmpty() {
        InMemoryAgentInstanceStore store = new InMemoryAgentInstanceStore();
        assertTrue(store.loadTransitions("nope").isEmpty());
    }
}
