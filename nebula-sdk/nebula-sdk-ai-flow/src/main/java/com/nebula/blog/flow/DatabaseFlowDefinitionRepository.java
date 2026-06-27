package com.nebula.blog.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.entity.AiFlow;
import com.nebula.blog.entity.AiFlowEdge;
import com.nebula.blog.entity.AiFlowNode;
import com.nebula.blog.mapper.AiFlowEdgeMapper;
import com.nebula.blog.mapper.AiFlowMapper;
import com.nebula.blog.mapper.AiFlowNodeMapper;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据库版流程定义仓储
 * 以 MyBatis-Plus 从 ai_flow / ai_flow_node / ai_flow_edge 三表组装出 {@link FlowDefinition}，
 * 供 SDK 的 {@code FlowEngine} 运行。本 Bean 经 SDK FlowAutoConfiguration 的 {@code @ConditionalOnMissingBean}
 * 自动顶替默认内存实现 {@code InMemoryFlowDefinitionRepository}。
 *
 * <p>仅负责读取（运行期加载）。整图写入由业务侧服务直接操作三 Mapper（事务），不经此类。
 *
 * @author nebula
 */
@Component
@RequiredArgsConstructor
public class DatabaseFlowDefinitionRepository implements FlowDefinitionRepository {

    private final AiFlowMapper flowMapper;

    private final AiFlowNodeMapper nodeMapper;

    private final AiFlowEdgeMapper edgeMapper;

    @Override
    public FlowDefinition findByCode(String flowCode) {
        if (!StringUtils.hasText(flowCode)) {
            return null;
        }
        AiFlow flow = flowMapper.selectOne(new LambdaQueryWrapper<AiFlow>()
                .eq(AiFlow::getFlowCode, flowCode)
                .last("limit 1"));
        if (flow == null) {
            return null;
        }

        FlowDefinition def = FlowDefinitionConverter.toDefinition(flow);

        List<AiFlowNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<AiFlowNode>()
                .eq(AiFlowNode::getFlowCode, flowCode)
                .orderByAsc(AiFlowNode::getSortNo));
        for (AiFlowNode node : nodes) {
            def.getNodes().add(FlowDefinitionConverter.toNodeDefinition(node));
        }

        List<AiFlowEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<AiFlowEdge>()
                .eq(AiFlowEdge::getFlowCode, flowCode)
                .orderByAsc(AiFlowEdge::getSortNo));
        for (AiFlowEdge edge : edges) {
            def.getEdges().add(FlowDefinitionConverter.toEdgeDefinition(edge));
        }

        return def;
    }
}
