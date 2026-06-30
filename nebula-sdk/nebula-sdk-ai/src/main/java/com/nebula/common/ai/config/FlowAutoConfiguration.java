package com.nebula.common.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.tool.DefaultToolCallingService;
import com.nebula.common.ai.agent.tool.ToolCallingService;
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
import com.nebula.common.ai.flow.tool.HttpToolDefinition;
import com.nebula.common.ai.flow.tool.HttpToolProperties;
import com.nebula.common.ai.orchestration.Orchestrator;
import com.nebula.common.ai.properties.AiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(HttpToolProperties.class)
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
     * 内置 HTTP 请求工具。持有专用连接池与线程池，Bean 销毁时经 {@code destroyMethod=shutdown} 释放。
     *
     * @param httpToolProperties HTTP 工具配置（nebula.ai.tool.http.*）
     * @param objectMapper       JSON 处理器（缺省自建，序列化对象请求体）
     * @return HTTP 请求工具
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(HttpToolDefinition.class)
    public HttpToolDefinition httpToolDefinition(HttpToolProperties httpToolProperties,
                                                 ObjectProvider<ObjectMapper> objectMapper) {
        return new HttpToolDefinition(httpToolProperties, objectMapper.getIfAvailable(ObjectMapper::new));
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
     * 工具调用闭环服务（function-calling loop）。
     * 驱动「调模型 → 模型自主选工具 → 执行 → 回灌 → 再调」的循环，内置迭代上限/异常隔离/白名单/审计/超时治理。
     * 持有工具执行线程池，Bean 销毁时经 {@code destroyMethod=shutdown} 释放。
     *
     * @param aiService    AI服务（每轮模型调用复用其过滤器链与日志）
     * @param toolRegistry 工具注册表
     * @param objectMapper JSON 处理器（解析模型生成的工具入参、序列化工具产物）
     * @param aiProperties AI配置属性（取 tool-calling 迭代上限与超时）
     * @return 工具调用闭环服务
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public ToolCallingService toolCallingService(AiService aiService,
                                                 ToolRegistry toolRegistry,
                                                 ObjectProvider<ObjectMapper> objectMapper,
                                                 AiProperties aiProperties) {
        return new DefaultToolCallingService(aiService, toolRegistry,
                objectMapper.getIfAvailable(ObjectMapper::new), aiProperties);
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
