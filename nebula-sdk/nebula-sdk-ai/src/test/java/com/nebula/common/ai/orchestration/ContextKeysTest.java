package com.nebula.common.ai.orchestration;

import com.nebula.common.ai.agent.AgentEngine;
import com.nebula.common.ai.agent.AgentNodeExecutor;
import com.nebula.common.ai.flow.EndNodeExecutor;
import com.nebula.common.ai.flow.FlowEngine;
import com.nebula.common.ai.flow.LoopNodeExecutor;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ContextKeys} 注册表单测：断言各执行器旧常量别名 == 注册表权威值（防字面量打错致键漂移），
 * 及保留前缀判定。
 *
 * @author nebula
 */
class ContextKeysTest {

    @Test
    void 旧常量别名与注册表权威值一致() {
        assertEquals(ContextKeys.System.FLOW_CODE, FlowEngine.FLOW_CODE_KEY);
        assertEquals(ContextKeys.System.RUN_ID, FlowEngine.RUN_ID_KEY);
        assertEquals(ContextKeys.System.CURRENT_INSTANCE_ID, AgentEngine.CURRENT_INSTANCE_KEY);
        assertEquals(ContextKeys.System.MAX_AGENT_DEPTH, AgentEngine.MAX_AGENT_DEPTH_KEY);
        assertEquals(ContextKeys.Agent.SIGNAL_EVENT, AgentEngine.SIGNAL_EVENT_KEY);
        assertEquals(ContextKeys.Agent.CALL_STACK, AgentNodeExecutor.CALL_STACK_KEY);
        assertEquals(ContextKeys.Output.OUTPUT, EndNodeExecutor.OUTPUT_KEY);
        assertEquals(ContextKeys.Loop.ITEM, LoopNodeExecutor.LOOP_ITEM_KEY);
        assertEquals(ContextKeys.Loop.INDEX, LoopNodeExecutor.LOOP_INDEX_KEY);
        assertEquals(ContextKeys.Loop.COUNT, LoopNodeExecutor.LOOP_COUNT_KEY);
        assertEquals(ContextKeys.Loop.RESULTS, LoopNodeExecutor.LOOP_RESULTS_KEY);
        assertEquals(ContextKeys.Loop.LAST_RESULT, LoopNodeExecutor.LOOP_LAST_RESULT_KEY);
        assertEquals(ContextKeys.StateMachine.ERROR, StateMachineOrchestrator.FAILED_ERROR_KEY);
        assertEquals(ContextKeys.StateMachine.SUSPENDED, StateMachineOrchestrator.SUSPENDED_KEY);
    }

    @Test
    void 全部保留键都带保留前缀且被登记() {
        for (String key : ContextKeys.all()) {
            assertTrue(ContextKeys.isReserved(key), key + " 应带保留前缀");
        }
        assertEquals(14, ContextKeys.all().size());
    }

    @Test
    void isReserved判定() {
        assertTrue(ContextKeys.isReserved("__output"));
        assertFalse(ContextKeys.isReserved("answer"));
        assertFalse(ContextKeys.isReserved(null));
        assertFalse(ContextKeys.isReserved(""));
    }
}
