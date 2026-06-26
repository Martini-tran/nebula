package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用节点执行器（type=PROMPT）：把节点执行记录写入上下文，并记录每个节点解析到的档案编码。
 *
 * @author nebula
 */
class RecordingNodeExecutor implements FlowNodeExecutor {

    final Map<String, String> profileSeen = new LinkedHashMap<>();

    @Override
    public String type() {
        return "PROMPT";
    }

    @Override
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        profileSeen.put(node.getNodeCode(), node.getProfileCode());
        ctx.put(node.getNodeCode(), "done");
    }
}
