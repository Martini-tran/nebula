package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用计数执行器（type=PROMPT）：记录每个节点跨所有（含子）上下文的累计执行次数，
 * 并按执行顺序收集各节点看到的 {@code __loopItem}，用于断言循环遍历次数与遍历项序列。
 *
 * @author nebula
 */
class CountingNodeExecutor implements FlowNodeExecutor {

    /**
     * nodeCode → 累计执行次数
     */
    final Map<String, Integer> execCount = new ConcurrentHashMap<>();

    /**
     * nodeCode → 各次执行看到的 __loopItem（保持顺序）
     */
    final Map<String, List<Object>> itemsSeen = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "PROMPT";
    }

    @Override
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx) {
        execCount.merge(node.getNodeCode(), 1, Integer::sum);
        itemsSeen.computeIfAbsent(node.getNodeCode(), k -> new ArrayList<>())
                .add(ctx.get(LoopNodeExecutor.LOOP_ITEM_KEY));
        // 写一个产物，便于循环体无 END 时 __loopResults 快照非空
        ctx.put(node.getNodeCode() + "_out", "done#" + execCount.get(node.getNodeCode()));
    }

    int count(String nodeCode) {
        return execCount.getOrDefault(nodeCode, 0);
    }
}
