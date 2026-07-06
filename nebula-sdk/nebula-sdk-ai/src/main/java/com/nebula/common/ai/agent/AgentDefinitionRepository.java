package com.nebula.common.ai.agent;

/**
 * Agent 定义仓储（SPI）
 * 按 {@code agentCode} 取 Agent 静态定义（{@link AgentDefinition}），支撑 1 Flow : N Agent 复用。
 * SDK 提供内存实现 {@link InMemoryAgentDefinitionRepository}；数据库实现由 {@code nebula-sdk-ai-flow} 的
 * store 包提供（读 {@code ai_agent} 表），装配时以 {@code @ConditionalOnMissingBean} 覆盖内存版。
 *
 * @author nebula
 */
public interface AgentDefinitionRepository {

    /**
     * 取指定 Agent 的当前启用定义（最新版本）。
     *
     * @param agentCode Agent 编码
     * @return 定义；不存在返回 null
     */
    AgentDefinition find(String agentCode);

    /**
     * 取指定 Agent 指定版本的定义（审计 / 版本锁定用）。
     *
     * @param agentCode Agent 编码
     * @param version   版本
     * @return 定义；不存在返回 null
     */
    AgentDefinition find(String agentCode, int version);
}
