package com.nebula.common.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinitionRepository;
import com.nebula.common.ai.flow.FlowEngine;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeExecutor;
import com.nebula.common.ai.flow.InMemoryFlowDefinitionRepository;
import com.nebula.common.ai.flow.InMemoryModelProfileRepository;
import com.nebula.common.ai.flow.ModelProfileRepository;
import com.nebula.common.ai.flow.PromptNodeExecutor;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolNodeExecutor;
import com.nebula.common.ai.flow.ToolRegistry;
import com.nebula.common.ai.flow.tool.EchoToolDefinition;
import com.nebula.common.ai.orchestration.Orchestrator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 配置化流程编排自动装配
 * 在 AI 运行时已装配（存在 {@link AiService}）的前提下，提供流程引擎所需的全部组件：条件编译器、
 * 模型档案/流程定义仓储（默认内存实现，业务可覆盖为数据库实现）、提示词节点执行器、流程图工厂与流程引擎。
 *
 * @author nebula
 */
@AutoConfiguration(after = {AiAutoConfiguration.class, AiAgentAutoConfiguration.class})
@ConditionalOnBean(AiService.class)
public class FlowAutoConfiguration {

    /**
     * 条件编译器（SpEL）
     */
    @Bean
    @ConditionalOnMissingBean
    public ConditionCompiler conditionCompiler() {
        return new ConditionCompiler();
    }

    /**
     * 模型档案仓储，默认内存实现
     */
    @Bean
    @ConditionalOnMissingBean
    public ModelProfileRepository modelProfileRepository() {
        return new InMemoryModelProfileRepository();
    }

    /**
     * 流程定义仓储，默认内存实现
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowDefinitionRepository flowDefinitionRepository() {
        return new InMemoryFlowDefinitionRepository();
    }

    /**
     * 提示词节点执行器（PROMPT 类型）
     *
     * @param aiService         AI服务
     * @param profileRepository 模型档案仓储
     * @param objectMapper      JSON处理器（缺省自建）
     * @return 提示词节点执行器
     */
    @Bean
    @ConditionalOnMissingBean(PromptNodeExecutor.class)
    public PromptNodeExecutor promptNodeExecutor(AiService aiService,
                                                 ModelProfileRepository profileRepository,
                                                 ObjectProvider<ObjectMapper> objectMapper) {
        return new PromptNodeExecutor(aiService, profileRepository, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * 内置回声工具（示例）。业务方定义自有工具时同样实现 {@link ToolDefinition} 并注册为 Bean 即可。
     *
     * @return 回声工具
     */
    @Bean
    @ConditionalOnMissingBean(EchoToolDefinition.class)
    public EchoToolDefinition echoToolDefinition() {
        return new EchoToolDefinition();
    }

    /**
     * 工具注册表，聚合容器中全部工具定义
     *
     * @param definitions 工具定义
     * @return 工具注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(ObjectProvider<ToolDefinition> definitions) {
        return new ToolRegistry(definitions.orderedStream().toList());
    }

    /**
     * 工具节点执行器（TOOL 类型）
     *
     * @param toolRegistry 工具注册表
     * @return 工具节点执行器
     */
    @Bean
    @ConditionalOnMissingBean(ToolNodeExecutor.class)
    public ToolNodeExecutor toolNodeExecutor(ToolRegistry toolRegistry) {
        return new ToolNodeExecutor(toolRegistry);
    }

    /**
     * 流程图工厂，聚合容器中全部节点执行器
     *
     * @param executors         节点执行器
     * @param conditionCompiler 条件编译器
     * @return 流程图工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowGraphFactory flowGraphFactory(ObjectProvider<FlowNodeExecutor> executors,
                                             ConditionCompiler conditionCompiler) {
        return new FlowGraphFactory(executors.orderedStream().toList(), conditionCompiler);
    }

    /**
     * 流程引擎
     *
     * @param flowRepository 流程定义仓储
     * @param graphFactory   流程图工厂
     * @param orchestrator   编排器
     * @return 流程引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowEngine flowEngine(FlowDefinitionRepository flowRepository,
                                 FlowGraphFactory graphFactory,
                                 Orchestrator orchestrator) {
        return new FlowEngine(flowRepository, graphFactory, orchestrator);
    }
}
