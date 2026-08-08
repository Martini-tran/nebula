package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.harness.draft.DraftIssue;

import java.util.List;
import java.util.Map;

/** 校验模拟输入并按 engineType 分派到权威模拟器。 */
public class DraftSimulator {

    private final Map<String, EngineSimulator> simulators;
    private final SimulationInputValidator inputValidator;

    public DraftSimulator(List<EngineSimulator> simulators, SimulationInputValidator inputValidator) {
        if (simulators == null) {
            this.simulators = Map.of();
        } else {
            Map<String, EngineSimulator> registered = new java.util.LinkedHashMap<>();
            simulators.forEach(simulator -> registered.putIfAbsent(simulator.engineType(), simulator));
            this.simulators = Map.copyOf(registered);
        }
        this.inputValidator = inputValidator;
    }

    public SimulationReport simulate(FlowDefinition definition, Map<String, Object> initialInput) {
        List<DraftIssue> inputIssues = inputValidator.validate(initialInput);
        if (!inputIssues.isEmpty()) {
            return new SimulationReport(definition.getEngineType(), SimulationConfidence.CONFIRMED,
                    inputIssues, Map.of());
        }
        EngineSimulator simulator = simulators.get(definition.getEngineType());
        if (simulator == null) {
            return new SimulationReport(definition.getEngineType(), SimulationConfidence.CONFIRMED,
                    List.of(DraftIssue.error("SIMULATOR_NOT_FOUND", null, "engineType",
                            "未注册执行内核模拟器: " + definition.getEngineType(),
                            "检查 Harness 模拟器自动装配")), Map.of());
        }
        return simulator.simulate(definition, initialInput == null ? Map.of() : initialInput);
    }
}
