package com.nebula.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowEngine;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.store.AiFlow;
import com.nebula.common.ai.flow.store.AiFlowEdge;
import com.nebula.common.ai.flow.store.AiFlowEdgeMapper;
import com.nebula.common.ai.flow.store.AiFlowMapper;
import com.nebula.common.ai.flow.store.AiFlowNode;
import com.nebula.common.ai.flow.store.AiFlowNodeMapper;
import com.nebula.common.ai.flow.store.DatabaseFlowDefinitionRepository;
import com.nebula.common.ai.flow.store.FlowDefinitionConverter;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.FlowPageQuery;
import com.nebula.manager.dto.FlowRunRequest;
import com.nebula.manager.service.FlowAdminService;
import com.nebula.manager.vo.FlowRunResultVO;
import com.nebula.manager.vo.FlowSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI流程编排管理服务实现（管理员端）
 * 读路径复用 {@link DatabaseFlowDefinitionRepository}（与引擎同一组装逻辑）；写路径直接操作三表，
 * 按「流程编码 upsert 头 + 节点/边全删再插」的事务策略保存整图，任何写操作后失效引擎缓存。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class FlowAdminServiceImpl implements FlowAdminService {

    private final AiFlowMapper flowMapper;

    private final AiFlowNodeMapper nodeMapper;

    private final AiFlowEdgeMapper edgeMapper;

    private final DatabaseFlowDefinitionRepository flowDefinitionRepository;

    private final FlowEngine flowEngine;

    @Override
    public PageResult<FlowSummaryVO> page(FlowPageQuery query) {
        FlowPageQuery safe = query == null ? new FlowPageQuery() : query;
        Page<AiFlow> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiFlow> wrapper = new LambdaQueryWrapper<AiFlow>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiFlow::getFlowCode, safe.getKeyword())
                        .or()
                        .like(AiFlow::getName, safe.getKeyword()))
                .eq(safe.getStatus() != null, AiFlow::getStatus, safe.getStatus())
                .orderByDesc(AiFlow::getUpdateTime);
        Page<AiFlow> result = flowMapper.selectPage(page, wrapper);
        List<FlowSummaryVO> rows = result.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public FlowDefinition getDefinition(String flowCode) {
        FlowDefinition def = flowDefinitionRepository.findByCode(flowCode);
        if (def == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "流程不存在: " + flowCode);
        }
        return def;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(FlowDefinition definition) {
        validateDefinition(definition);
        String flowCode = definition.getFlowCode();

        AiFlow existing = flowMapper.selectOne(new LambdaQueryWrapper<AiFlow>()
                .eq(AiFlow::getFlowCode, flowCode)
                .last("limit 1"));
        AiFlow head = existing == null ? new AiFlow() : existing;
        head.setFlowCode(flowCode);
        head.setName(definition.getName());
        head.setDescription(definition.getDescription());
        head.setVersion(definition.getVersion() <= 0 ? 1 : definition.getVersion());
        head.setDefaultProfileCode(definition.getDefaultProfileCode());
        if (head.getStatus() == null) {
            head.setStatus(1);
        }
        if (existing == null) {
            flowMapper.insert(head);
        } else {
            flowMapper.updateById(head);
        }

        // 节点/边全删再插（MVP 最简可靠策略）
        nodeMapper.delete(new LambdaQueryWrapper<AiFlowNode>().eq(AiFlowNode::getFlowCode, flowCode));
        edgeMapper.delete(new LambdaQueryWrapper<AiFlowEdge>().eq(AiFlowEdge::getFlowCode, flowCode));

        List<FlowNodeDefinition> nodes = definition.getNodes();
        if (nodes != null) {
            for (FlowNodeDefinition node : nodes) {
                nodeMapper.insert(FlowDefinitionConverter.toNodeEntity(flowCode, node));
            }
        }
        List<FlowEdgeDefinition> edges = definition.getEdges();
        if (edges != null) {
            for (FlowEdgeDefinition edge : edges) {
                edgeMapper.insert(FlowDefinitionConverter.toEdgeEntity(flowCode, edge));
            }
        }

        flowEngine.evict(flowCode);
        return flowCode;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String flowCode) {
        if (!StringUtils.hasText(flowCode)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "流程编码不能为空");
        }
        flowMapper.delete(new LambdaQueryWrapper<AiFlow>().eq(AiFlow::getFlowCode, flowCode));
        nodeMapper.delete(new LambdaQueryWrapper<AiFlowNode>().eq(AiFlowNode::getFlowCode, flowCode));
        edgeMapper.delete(new LambdaQueryWrapper<AiFlowEdge>().eq(AiFlowEdge::getFlowCode, flowCode));
        flowEngine.evict(flowCode);
    }

    @Override
    public FlowRunResultVO run(String flowCode, FlowRunRequest request) {
        if (!StringUtils.hasText(flowCode)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "流程编码不能为空");
        }
        FlowRunRequest safe = request == null ? new FlowRunRequest() : request;
        Long userId = UserContext.getUserId();

        OrchestrationContext ctx = flowEngine.run(
                flowCode,
                safe.getInput(),
                userId == null ? null : String.valueOf(userId),
                safe.getConversationId());

        return toResultVO(flowCode, ctx);
    }

    @Override
    public FlowRunResultVO resume(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "执行实例标识不能为空");
        }
        OrchestrationContext ctx = flowEngine.resume(runId);
        return toResultVO(ctx.getString(FlowEngine.FLOW_CODE_KEY), ctx);
    }

    /**
     * 把编排上下文转为运行结果 VO：产物、节点轨迹与执行实例标识。
     */
    private FlowRunResultVO toResultVO(String flowCode, OrchestrationContext ctx) {
        FlowRunResultVO vo = new FlowRunResultVO();
        vo.setFlowCode(flowCode);
        vo.setRunId(ctx.getString(FlowEngine.RUN_ID_KEY));
        vo.getAttributes().putAll(ctx.attributes());
        vo.getNodeResults().putAll(ctx.nodeResults());
        return vo;
    }

    private void validateDefinition(FlowDefinition definition) {
        if (definition == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "流程定义不能为空");
        }
        if (!StringUtils.hasText(definition.getFlowCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "流程编码不能为空");
        }
    }

    private FlowSummaryVO toSummary(AiFlow entity) {
        FlowSummaryVO vo = new FlowSummaryVO();
        vo.setFlowCode(entity.getFlowCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setVersion(entity.getVersion());
        vo.setDefaultProfileCode(entity.getDefaultProfileCode());
        vo.setStatus(entity.getStatus());
        Long count = nodeMapper.selectCount(new LambdaQueryWrapper<AiFlowNode>()
                .eq(AiFlowNode::getFlowCode, entity.getFlowCode()));
        vo.setNodeCount(count == null ? 0 : count.intValue());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
