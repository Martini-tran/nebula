package com.nebula.common.ai.harness.validate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.harness.draft.DraftIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftRuleSetTest {

    @Test
    void stateMachineRejectsUnreachableTerminalAndWarnsAboutMissingDefaultTransition() {
        FlowDefinition definition = new FlowDefinition().setEngineType("STATE_MACHINE");
        definition.getNodes().add(state("entry", "ENTRY"));
        definition.getNodes().add(state("work", "NORMAL"));
        definition.getNodes().add(state("done", "TERMINAL"));
        definition.getEdges().add(edge("entry", "work", "getString('approved') == 'true'"));

        List<DraftIssue> issues = new StateMachineRuleSet().validate(definition);

        assertTrue(hasCode(issues, "TERMINAL_UNREACHABLE"));
        assertTrue(hasCode(issues, "UNREACHABLE_STATE"));
        assertTrue(hasCode(issues, "MISSING_DEFAULT_TRANSITION"));
    }

    @Test
    void stateMachineRejectsTerminalOutgoingEdge() {
        FlowDefinition definition = new FlowDefinition().setEngineType("STATE_MACHINE");
        definition.getNodes().add(state("entry", "ENTRY"));
        definition.getNodes().add(state("done", "TERMINAL"));
        definition.getEdges().add(edge("entry", "done", null));
        definition.getEdges().add(edge("done", "entry", null));

        List<DraftIssue> issues = new StateMachineRuleSet().validate(definition);

        assertTrue(hasCode(issues, "TERMINAL_HAS_OUTGOING_EDGE"));
    }

    @Test
    void commonRulesDistinguishDominatingInputFromBranchOnlyOutput() {
        FlowDefinition definition = new FlowDefinition().setEngineType("DAG");
        FlowNodeDefinition start = new FlowNodeDefinition()
                .setNodeCode("start").setNodeType("START")
                .setNodeConfig(Map.of("inputs", Map.of("topic", "")));
        FlowNodeDefinition left = new FlowNodeDefinition()
                .setNodeCode("left").setNodeType("PROMPT").setOutputKey("content");
        FlowNodeDefinition right = new FlowNodeDefinition()
                .setNodeCode("right").setNodeType("PROMPT");
        FlowNodeDefinition join = new FlowNodeDefinition()
                .setNodeCode("join").setNodeType("PROMPT")
                .setPromptTemplate("主题 #{topic}，内容 #{content}");
        definition.setNodes(List.of(start, left, right, join));
        definition.setEdges(List.of(
                edge("start", "left", null),
                edge("start", "right", null),
                edge("left", "join", null),
                edge("right", "join", null)));

        List<DraftIssue> issues = new CommonRules().validate("DAG", definition);

        assertTrue(hasCode(issues, "TEMPLATE_VARIABLE_NOT_DOMINATED"));
        assertFalse(hasCode(issues, "UNRESOLVED_TEMPLATE_VARIABLE"));
    }

    private static FlowNodeDefinition state(String code, String type) {
        return new FlowNodeDefinition().setNodeCode(code).setNodeType("PROMPT").setStateType(type);
    }

    private static FlowEdgeDefinition edge(String from, String to, String condition) {
        return new FlowEdgeDefinition().setFromNode(from).setToNode(to).setConditionExpr(condition);
    }

    private static boolean hasCode(List<DraftIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> code.equals(issue.code()));
    }
}
