package com.nebula.common.ai.harness.simulate;

import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.draft.DraftIssue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 模拟器共享的模板输入与入口默认值处理。 */
final class SimulationSupport {

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile(
            "\\{\\{\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*}}|#\\{\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*}");

    private SimulationSupport() {
    }

    static void applyEntryDefaults(FlowNodeDefinition node, SimulationContext context) {
        if (node.getNodeConfig() == null || !(node.getNodeConfig().get("inputs") instanceof Map<?, ?> inputs)) {
            return;
        }
        inputs.forEach((key, value) -> {
            String name = key == null ? null : String.valueOf(key);
            if (name != null && !context.contains(name)) {
                context.putKnown(name, value);
            }
        });
    }

    static List<DraftIssue> unresolvedInputs(FlowNodeDefinition node, SimulationContext context) {
        Set<String> variables = new LinkedHashSet<>();
        variables.addAll(templateVariables(node.getSystemPrompt()));
        variables.addAll(templateVariables(node.getPromptTemplate()));
        return variables.stream().map(variable -> {
            String key = node.getInputMapping() == null
                    ? variable : node.getInputMapping().getOrDefault(variable, variable);
            if (context.contains(key)) {
                return null;
            }
            return DraftIssue.error("UNRESOLVED_VAR", node.getNodeCode(), "inputMapping",
                    "模拟执行到节点时缺少上下文键: " + key,
                    "通过 initialInput 提供该键，或调整上游 outputKey 与连边");
        }).filter(java.util.Objects::nonNull).toList();
    }

    private static Set<String> templateVariables(String template) {
        Set<String> result = new LinkedHashSet<>();
        if (template == null || template.isBlank()) {
            return result;
        }
        Matcher matcher = TEMPLATE_VARIABLE.matcher(template);
        while (matcher.find()) {
            result.add(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
        }
        return result;
    }
}
