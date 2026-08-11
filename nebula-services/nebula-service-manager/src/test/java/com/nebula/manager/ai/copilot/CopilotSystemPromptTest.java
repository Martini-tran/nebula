package com.nebula.manager.ai.copilot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSystemPromptTest {

    @Test
    void requiresFlowAwareTestInputForSimulationAndRealRun() {
        String prompt = CopilotSystemPrompt.TEXT;

        assertTrue(prompt.contains("测试参数纪律"));
        assertTrue(prompt.contains("读取入口节点详情"));
        assertTrue(prompt.contains("优先使用 example，其次 defaultValue"));
        assertTrue(prompt.contains("simulate_draft 与 real_run_draft 必须复用完全相同的 initialInput"));
        assertTrue(prompt.contains("没有声明任何入口参数时 initialInput 必须显式传 {}"));
    }
}
