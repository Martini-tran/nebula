package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowDefinition;

import java.util.Map;

/** 单一流程引擎的无副作用模拟 SPI。 */
public interface EngineSimulator {

    String engineType();

    SimulationReport simulate(FlowDefinition definition, Map<String, Object> initialInput);
}
