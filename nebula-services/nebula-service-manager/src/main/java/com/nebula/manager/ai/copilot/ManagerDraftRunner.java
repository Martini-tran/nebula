package com.nebula.manager.ai.copilot;

import com.nebula.common.ai.flow.FlowEngine;
import com.nebula.common.ai.harness.realrun.DraftRunRequest;
import com.nebula.common.ai.harness.realrun.DraftRunResult;
import com.nebula.common.ai.harness.realrun.DraftRunner;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Manager 对草稿隔离执行 SPI 的适配，不经过正式流程仓储和运行状态存储。 */
@Component
@RequiredArgsConstructor
public class ManagerDraftRunner implements DraftRunner {

    private final FlowEngine flowEngine;

    @Override
    public DraftRunResult run(DraftRunRequest request) {
        OrchestrationContext context = flowEngine.runDefinition(
                request.definition(),
                request.initialInput(),
                request.userId(),
                request.sessionId());
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("attributes", new LinkedHashMap<>(context.attributes()));
        output.put("nodeResults", new LinkedHashMap<>(context.nodeResults()));
        return new DraftRunResult(output);
    }
}
