package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReservedKeyValidator} 单测：业务节点 outputKey 占用保留前缀 __ 时构图期报错。
 *
 * @author nebula
 */
class ReservedKeyValidatorTest {

    private FlowNodeDefinition node(String code, String outputKey) {
        return new FlowNodeDefinition().setNodeCode(code).setNodeType("PROMPT").setOutputKey(outputKey);
    }

    @Test
    void 正常outputKey通过() {
        assertDoesNotThrow(() -> ReservedKeyValidator.check(List.of(
                node("a", "answer"), node("b", "score"), node("c", null))));
    }

    @Test
    void 保留前缀outputKey报错() {
        OrchestrationException ex = assertThrows(OrchestrationException.class,
                () -> ReservedKeyValidator.check(List.of(node("bad", "__output"))));
        assertTrue(ex.getMessage().contains("__output"));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void 多个违规聚合报出() {
        OrchestrationException ex = assertThrows(OrchestrationException.class,
                () -> ReservedKeyValidator.check(List.of(
                        node("x", "__loopItem"), node("y", "ok"), node("z", "__runId"))));
        assertTrue(ex.getMessage().contains("__loopItem"));
        assertTrue(ex.getMessage().contains("__runId"));
    }

    @Test
    void 空列表通过() {
        assertDoesNotThrow(() -> ReservedKeyValidator.check(null));
        assertDoesNotThrow(() -> ReservedKeyValidator.check(List.of()));
    }

    @Test
    void 经由FlowGraphFactory构图期拦截() {
        FlowDefinition def = new FlowDefinition().setFlowCode("f");
        def.getNodes().add(node("n", "__output").setPromptTemplate("x"));
        FlowGraphFactory factory = new FlowGraphFactory(
                List.of(new RecordingNodeExecutor()), new ConditionCompiler());
        OrchestrationException ex = assertThrows(OrchestrationException.class, () -> factory.build(def));
        assertTrue(ex.getMessage().contains("保留键"));
    }
}
