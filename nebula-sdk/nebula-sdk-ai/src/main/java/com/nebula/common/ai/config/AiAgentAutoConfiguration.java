package com.nebula.common.ai.config;

import com.nebula.common.ai.agent.Agent;
import com.nebula.common.ai.agent.AgentRegistry;
import com.nebula.common.ai.agent.InMemoryAgentRegistry;
import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.orchestration.Orchestrator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * AI Agent 与编排自动装配
 * 收集容器中全部{@link Agent} Bean 注册进{@link AgentRegistry}，使编排节点可按功能编码取用各 Agent；
 * 并提供默认编排器{@link DagOrchestrator}。具体编排图与{@code OrchestrationAgent}由各业务模块按需定义，
 * 故此处不预置。
 *
 * @author nebula
 */
@AutoConfiguration
public class AiAgentAutoConfiguration {

    /**
     * 构建 Agent 注册表，并自动注册容器内全部 Agent
     *
     * @param agents 容器中全部 Agent
     * @return Agent 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry(ObjectProvider<Agent> agents) {
        AgentRegistry registry = new InMemoryAgentRegistry();
        agents.orderedStream().forEach(registry::register);
        return registry;
    }

    /**
     * 默认 DAG 编排器
     *
     * @return 编排器
     */
    @Bean
    @ConditionalOnMissingBean
    public Orchestrator dagOrchestrator() {
        return new DagOrchestrator();
    }
}
