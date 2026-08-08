package com.nebula.common.ai.harness.validate;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.harness.draft.DraftIssue;

import java.util.List;

/**
 * 单一流程引擎的全图语义规则集。
 *
 * <p>规则集只读取传入定义，不得修改草稿或补默认值。
 *
 * @author nebula
 */
public interface EngineRuleSet {

    String engineType();

    List<DraftIssue> validate(FlowDefinition definition);
}
