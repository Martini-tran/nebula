package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.orchestration.OrchestrationContext;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 单条模拟路径的隔离上下文，并追踪由替身生成的不确定值。 */
public final class SimulationContext {

    private final Map<String, Object> values;
    private final Set<String> syntheticKeys;

    public SimulationContext(Map<String, Object> initialInput) {
        this.values = new LinkedHashMap<>(initialInput == null ? Map.of() : initialInput);
        this.syntheticKeys = new LinkedHashSet<>();
    }

    private SimulationContext(Map<String, Object> values, Set<String> syntheticKeys) {
        this.values = new LinkedHashMap<>(values);
        this.syntheticKeys = new LinkedHashSet<>(syntheticKeys);
    }

    public SimulationContext copy() {
        return new SimulationContext(values, syntheticKeys);
    }

    public void putKnown(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            values.put(key, value);
            syntheticKeys.remove(key);
        }
    }

    public void putSynthetic(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            values.put(key, value);
            syntheticKeys.add(key);
        }
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public boolean isSynthetic(String key) {
        return syntheticKeys.contains(key);
    }

    public Set<String> keys() {
        return Set.copyOf(values.keySet());
    }

    public OrchestrationContext orchestrationContext() {
        OrchestrationContext context = new OrchestrationContext();
        values.forEach(context::put);
        return context;
    }
}
