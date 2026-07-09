package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.ai.agent.AgentDefinition;
import com.nebula.common.ai.agent.AgentDefinitionRepository;

/**
 * 数据库版 Agent 定义仓储
 * 读 {@code ai_agent} 表按 agentCode 组装 {@link AgentDefinition} 供 {@code AgentEngine} 运行，覆盖 SDK 默认内存实现。
 * 与 {@link DatabaseFlowDefinitionRepository} 同构：由 {@link AiAgentStoreAutoConfiguration} 装配。
 *
 * @author nebula
 */
public class DatabaseAgentDefinitionRepository implements AgentDefinitionRepository {

    private final AiAgentMapper agentMapper;

    public DatabaseAgentDefinitionRepository(AiAgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    @Override
    public AgentDefinition find(String agentCode) {
        if (agentCode == null) {
            return null;
        }
        // 取同 agentCode 下版本号最大的启用定义
        AiAgent entity = agentMapper.selectOne(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getAgentCode, agentCode)
                .eq(AiAgent::getStatus, 1)
                .orderByDesc(AiAgent::getVersion)
                .last("limit 1"));
        return toDefinition(entity);
    }

    @Override
    public AgentDefinition find(String agentCode, int version) {
        if (agentCode == null) {
            return null;
        }
        AiAgent entity = agentMapper.selectOne(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getAgentCode, agentCode)
                .eq(AiAgent::getVersion, version)
                .last("limit 1"));
        return toDefinition(entity);
    }

    private AgentDefinition toDefinition(AiAgent entity) {
        if (entity == null) {
            return null;
        }
        return new AgentDefinition()
                .setAgentCode(entity.getAgentCode())
                .setName(entity.getName())
                .setDescription(entity.getDescription())
                .setFlowCode(entity.getFlowCode())
                .setFlowVersion(entity.getFlowVersion() == null ? 1 : entity.getFlowVersion())
                .setInputSchema(entity.getInputSchema())
                .setOutputSchema(entity.getOutputSchema())
                .setMemoryConfig(entity.getMemoryConfig())
                .setDefaultProfileCode(entity.getDefaultProfileCode())
                .setVersion(entity.getVersion() == null ? 1 : entity.getVersion())
                .setStatus(entity.getStatus() == null ? 1 : entity.getStatus());
    }
}
