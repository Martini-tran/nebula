package com.nebula.common.ai.harness.validate;

import com.nebula.common.ai.flow.CondGroupCompiler;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.FlowStateMachineFactory;
import com.nebula.common.ai.harness.draft.DraftFieldValidator;
import com.nebula.common.ai.harness.draft.DraftIssue;
import com.nebula.common.ai.harness.draft.EngineTypeCatalog;
import com.nebula.common.ai.harness.draft.FlowDefinitionCodec;
import com.nebula.common.ai.harness.draft.FlowDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程草稿全量校验入口。
 *
 * <p>按“字段/结构与数据流、条件语法、引擎编译”逐层深入。编译始终使用深拷贝，避免工厂补默认档案时
 * 修改 canonical 草稿。
 *
 * @author nebula
 */
public class DraftValidator {

    private final DraftFieldValidator fieldValidator;
    private final CommonRules commonRules;
    private final Map<String, EngineRuleSet> ruleSets;
    private final FlowGraphFactory graphFactory;
    private final FlowStateMachineFactory stateMachineFactory;
    private final ConditionCompiler conditionCompiler;
    private final CondGroupCompiler condGroupCompiler;
    private final FlowDefinitionCodec codec;

    public DraftValidator(DraftFieldValidator fieldValidator,
                          CommonRules commonRules,
                          List<EngineRuleSet> ruleSets,
                          FlowGraphFactory graphFactory,
                          FlowStateMachineFactory stateMachineFactory,
                          ConditionCompiler conditionCompiler,
                          CondGroupCompiler condGroupCompiler,
                          FlowDefinitionCodec codec) {
        this.fieldValidator = fieldValidator;
        this.commonRules = commonRules;
        this.ruleSets = new LinkedHashMap<>();
        if (ruleSets != null) {
            ruleSets.forEach(ruleSet -> this.ruleSets.put(ruleSet.engineType(), ruleSet));
        }
        this.graphFactory = graphFactory;
        this.stateMachineFactory = stateMachineFactory;
        this.conditionCompiler = conditionCompiler;
        this.condGroupCompiler = condGroupCompiler;
        this.codec = codec;
    }

    public List<DraftIssue> validate(FlowDraft source) {
        FlowDraft draft = copy(source);
        List<DraftIssue> issues = new ArrayList<>(fieldValidator.validate(draft));
        FlowDefinition definition = draft.getGraph();

        if (!hasText(definition.getFlowCode())) {
            issues.add(DraftIssue.error("MISSING_FLOW_CODE", null, "flowCode",
                    "提交前必须设置 flowCode", "调用 update_draft_metadata 补齐 flowCode"));
        }
        issues.addAll(commonRules.validate(draft.getEngineType(), definition));
        EngineRuleSet ruleSet = ruleSets.get(draft.getEngineType());
        if (ruleSet == null) {
            issues.add(DraftIssue.error("INVALID_ENGINE_TYPE", null, "engineType",
                    "没有找到 engineType=" + draft.getEngineType() + " 的校验规则集",
                    "将草稿引擎设置为 DAG 或 STATE_MACHINE"));
        } else {
            issues.addAll(ruleSet.validate(definition));
        }
        issues = distinct(issues);

        issues.addAll(validateConditions(definition));
        issues = distinct(issues);
        if (!hasErrors(issues)) {
            issues.addAll(compile(draft.getEngineType(), definition));
        }
        return distinct(issues);
    }

    private List<DraftIssue> validateConditions(FlowDefinition definition) {
        List<DraftIssue> issues = new ArrayList<>();
        for (FlowEdgeDefinition edge : definition.getEdges() == null ? List.<FlowEdgeDefinition>of()
                : definition.getEdges()) {
            try {
                conditionCompiler.compile(edge.getConditionExpr());
            } catch (RuntimeException e) {
                issues.add(DraftIssue.error("INVALID_CONDITION_EXPR", edge.getFromNode(), "conditionExpr",
                        e.getMessage(), "修正该边的 SpEL 表达式后重新校验"));
            }
        }
        for (FlowNodeDefinition node : definition.getNodes() == null ? List.<FlowNodeDefinition>of()
                : definition.getNodes()) {
            if (!"LOOP".equals(node.getNodeType()) || node.getNodeConfig() == null) {
                continue;
            }
            Object loop = node.getNodeConfig().get("loop");
            if (loop instanceof Map<?, ?> loopConfig) {
                // CondGroupCompiler 当前以 null 表示无有效条件；调用仍能覆盖结构化条件的编译兼容路径。
                condGroupCompiler.compile(loopConfig.get("breakCondition"));
            }
        }
        return issues;
    }

    private List<DraftIssue> compile(String engineType, FlowDefinition definition) {
        try {
            FlowDefinition copy = codec.copy(definition);
            if (EngineTypeCatalog.STATE_MACHINE.equals(engineType)) {
                stateMachineFactory.build(copy);
            } else {
                graphFactory.build(copy);
            }
            return List.of();
        } catch (RuntimeException e) {
            return List.of(DraftIssue.error("DRAFT_COMPILATION_FAILED", null, "graph",
                    e.getMessage(), "根据编译错误修正节点、边或执行器引用后重新校验"));
        }
    }

    private FlowDraft copy(FlowDraft source) {
        return new FlowDraft()
                .setDraftId(source.getDraftId())
                .setSessionId(source.getSessionId())
                .setUserId(source.getUserId())
                .setFlowCode(source.getFlowCode())
                .setName(source.getName())
                .setDescription(source.getDescription())
                .setEngineType(source.getEngineType())
                .setRevision(source.getRevision())
                .setLastValidatedRevision(source.getLastValidatedRevision())
                .setLastSimulatedRevision(source.getLastSimulatedRevision())
                .setStatus(source.getStatus())
                .setCommittedFlowCode(source.getCommittedFlowCode())
                .setGraph(codec.copy(source.getGraph()));
    }

    private List<DraftIssue> distinct(List<DraftIssue> issues) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(issues));
    }

    private boolean hasErrors(List<DraftIssue> issues) {
        return issues.stream().anyMatch(issue -> "ERROR".equals(issue.level()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
