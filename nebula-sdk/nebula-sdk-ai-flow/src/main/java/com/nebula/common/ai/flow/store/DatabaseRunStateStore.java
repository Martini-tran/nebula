package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.ai.orchestration.RunSnapshot;
import com.nebula.common.ai.orchestration.RunStateStore;
import com.nebula.common.ai.orchestration.RunStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 数据库版编排执行状态存储
 * 以 MyBatis-Plus 把编排执行进度落 {@code ai_flow_run}（实例头）与 {@code ai_flow_run_node}（节点轨迹）两表，
 * 支撑断点续跑。本 Bean 由 {@link AiFlowStoreAutoConfiguration} 以 {@code @ConditionalOnMissingBean} 装配；
 * 装配后 SDK {@code FlowEngine}/{@code DagOrchestrator} 获得状态持久化能力（缺失时退化为纯内存执行）。
 *
 * <p>JSON 列（input/attributes/executed_nodes/output）复用 {@link FlowJsonCodec} 序列化。runId 用 UUID 生成。
 * 每个写方法为单表/单实例操作，事务一致性由调用方按需控制。
 *
 * @author nebula
 */
@Slf4j
public class DatabaseRunStateStore implements RunStateStore {

    private final AiFlowRunMapper runMapper;

    private final AiFlowRunNodeMapper runNodeMapper;

    public DatabaseRunStateStore(AiFlowRunMapper runMapper, AiFlowRunNodeMapper runNodeMapper) {
        this.runMapper = runMapper;
        this.runNodeMapper = runNodeMapper;
    }

    @Override
    public String startRun(String flowCode, int version, String userId, String conversationId,
                           Map<String, Object> input) {
        String runId = flowCode + "-" + UUID.randomUUID().toString().replace("-", "");
        AiFlowRun run = new AiFlowRun();
        run.setRunId(runId);
        run.setFlowCode(flowCode);
        run.setVersion(version);
        run.setUserId(userId);
        run.setConversationId(conversationId);
        run.setStatus(RunStatus.RUNNING.name());
        run.setInput(FlowJsonCodec.write(input));
        run.setExecutedNodes(FlowJsonCodec.write(List.of()));
        runMapper.insert(run);
        return runId;
    }

    @Override
    public RunSnapshot load(String runId) {
        AiFlowRun run = findByRunId(runId);
        if (run == null) {
            return null;
        }
        Set<String> executed = new LinkedHashSet<>(FlowJsonCodec.readStringList(run.getExecutedNodes()));
        Map<String, Object> attributes = FlowJsonCodec.readObjectMap(run.getAttributes());
        RunStatus status = parseStatus(run.getStatus());
        return new RunSnapshot(run.getRunId(), run.getFlowCode(),
                run.getVersion() == null ? 1 : run.getVersion(),
                status, run.getUserId(), run.getConversationId(), executed, attributes);
    }

    @Override
    public void saveNodeDone(String runId, String nodeCode, int seq, Map<String, Object> attributesSnapshot) {
        AiFlowRun run = findByRunId(runId);
        if (run == null) {
            log.warn("落库节点完成快照找不到实例[{}]", runId);
            return;
        }
        // 追加已执行节点（去重保序）
        Set<String> executed = new LinkedHashSet<>(FlowJsonCodec.readStringList(run.getExecutedNodes()));
        executed.add(nodeCode);
        run.setExecutedNodes(FlowJsonCodec.write(List.copyOf(executed)));
        run.setAttributes(FlowJsonCodec.write(attributesSnapshot));
        run.setStatus(RunStatus.RUNNING.name());
        runMapper.updateById(run);

        // 记一条节点轨迹
        AiFlowRunNode node = new AiFlowRunNode();
        node.setRunId(runId);
        node.setNodeCode(nodeCode);
        node.setSeq(seq);
        node.setStatus(RunStatus.SUCCESS.name());
        node.setOutput(FlowJsonCodec.write(attributesSnapshot));
        runNodeMapper.insert(node);
    }

    @Override
    public void markFinished(String runId, Map<String, Object> attributesSnapshot) {
        AiFlowRun run = findByRunId(runId);
        if (run == null) {
            return;
        }
        run.setStatus(RunStatus.SUCCESS.name());
        run.setAttributes(FlowJsonCodec.write(attributesSnapshot));
        runMapper.updateById(run);
    }

    @Override
    public void markFailed(String runId, String failedNodeCode, String error, Map<String, Object> attributesSnapshot) {
        AiFlowRun run = findByRunId(runId);
        if (run == null) {
            return;
        }
        run.setStatus(RunStatus.FAILED.name());
        run.setFailedNode(failedNodeCode);
        run.setErrorMsg(error);
        run.setAttributes(FlowJsonCodec.write(attributesSnapshot));
        runMapper.updateById(run);
    }

    private AiFlowRun findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        return runMapper.selectOne(new LambdaQueryWrapper<AiFlowRun>()
                .eq(AiFlowRun::getRunId, runId)
                .last("limit 1"));
    }

    private RunStatus parseStatus(String status) {
        if (status == null) {
            return RunStatus.RUNNING;
        }
        try {
            return RunStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return RunStatus.RUNNING;
        }
    }
}
