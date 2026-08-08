package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.ConditionCompiler;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在已知上下文上求值 guard，并将缺失或占位输入显式归为 UNDECIDED。 */
public class SimulationConditionEvaluator {

    private static final Pattern CONTEXT_KEY = Pattern.compile(
            "(?:get|getString|contains)\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    private final ConditionCompiler compiler;

    public SimulationConditionEvaluator(ConditionCompiler compiler) {
        this.compiler = compiler;
    }

    public ConditionResult evaluate(String expression, SimulationContext context) {
        if (expression == null || expression.isBlank()) {
            return ConditionResult.TRUE;
        }
        for (String key : referencedKeys(expression)) {
            if (!context.contains(key) || context.isSynthetic(key)) {
                return ConditionResult.UNDECIDED;
            }
        }
        try {
            Predicate<com.nebula.common.ai.orchestration.OrchestrationContext> predicate = compiler.compile(expression);
            return predicate.test(context.orchestrationContext()) ? ConditionResult.TRUE : ConditionResult.FALSE;
        } catch (RuntimeException ignored) {
            return ConditionResult.UNDECIDED;
        }
    }

    private Set<String> referencedKeys(String expression) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = CONTEXT_KEY.matcher(expression);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    public enum ConditionResult {
        TRUE,
        FALSE,
        UNDECIDED
    }
}
