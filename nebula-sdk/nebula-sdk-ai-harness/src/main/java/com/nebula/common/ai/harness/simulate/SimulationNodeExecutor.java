package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowNodeDefinition;

/**
 * 节点模拟替身 SPI。实现只能写模拟上下文，禁止调用模型、真实工具或子 Agent。
 */
public interface SimulationNodeExecutor {

    boolean supports(String nodeType);

    void execute(FlowNodeDefinition node, SimulationContext context);
}
