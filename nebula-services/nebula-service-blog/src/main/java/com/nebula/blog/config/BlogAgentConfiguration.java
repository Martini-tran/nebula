package com.nebula.blog.config;

import com.nebula.agent.blog.BlogAgent;
import com.nebula.agent.blog.DefaultBlogAgent;
import com.nebula.agent.blog.domain.BlogSeriesOutline;
import com.nebula.agent.blog.domain.BlogSeriesRequest;
import com.nebula.blog.service.impl.BlogSeriesOutlineWriter;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.memory.AgentMemory;
import com.nebula.common.ai.memory.AgentMemoryConfig;
import com.nebula.common.ai.memory.AgentMemoryRegistry;
import com.nebula.common.ai.orchestration.OrchestrationAgent;
import com.nebula.common.ai.orchestration.OrchestrationGraph;
import com.nebula.common.ai.orchestration.Orchestrator;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 博客Agent装配
 * 仅当容器中存在 {@link AiService} 时才装配 {@link BlogAgent}：AI运行时（AiService）尚未接入前，
 * 不强制要求该Bean存在，避免博客服务无法启动。记忆门面按Agent功能编码从注册表取，未注册则为空。
 *
 * <p>系列生成被建模为一条编排流（{@link #blogSeriesOrchestrator}）：选题 → 落库两节点，由带记忆的
 * {@link OrchestrationAgent} 驱动，编排器自身按功能编码 {@value #SERIES_ORCHESTRATOR_CODE} 积累情景记忆。
 *
 * @author nebula
 */
@Configuration
public class BlogAgentConfiguration {

    /**
     * 系列生成编排器的功能编码（亦为其记忆隔离编码，与博客Agent的"blog"区分）
     */
    public static final String SERIES_ORCHESTRATOR_CODE = "blog-series";

    /**
     * 上下文键：系列生成请求（{@link BlogSeriesRequest}）
     */
    public static final String CTX_REQUEST = "request";

    /**
     * 上下文键：回退系列标题（请求主题）
     */
    public static final String CTX_TOPIC = "topic";

    /**
     * 上下文键：AI生成的系列选题大纲（{@link BlogSeriesOutline}）
     */
    public static final String CTX_OUTLINE = "outline";

    /**
     * 上下文键：落库后的新建系列ID（{@link Long}）
     */
    public static final String CTX_SERIES_ID = "seriesId";

    /**
     * 博客Agent记忆配置：开启长期记忆。
     * 该Bean被 AiMemoryAutoConfiguration 收集，为 "blog" 构建带长期记忆的记忆门面（长期记忆由
     * 容器中的 LongTermMemory 实现提供，如 {@code DatabaseLongTermMemory}）。无条件装配，
     * 记忆设施独立于AI运行时是否启用。
     *
     * @return 博客Agent记忆配置
     */
    @Bean
    @ConditionalOnMissingBean(name = "blogAgentMemoryConfig")
    public AgentMemoryConfig blogAgentMemoryConfig() {
        return new AgentMemoryConfig(DefaultBlogAgent.AGENT_CODE).setLongTermEnabled(true);
    }

    /**
     * 系列生成编排器记忆配置：开启长期记忆。
     * 使编排器（{@value #SERIES_ORCHESTRATOR_CODE}）拥有独立于博客Agent的记忆隔离空间，
     * 用于积累「历次系列生成」的情景记忆。
     *
     * @return 编排器记忆配置
     */
    @Bean
    @ConditionalOnMissingBean(name = "blogSeriesOrchestratorMemoryConfig")
    public AgentMemoryConfig blogSeriesOrchestratorMemoryConfig() {
        return new AgentMemoryConfig(SERIES_ORCHESTRATOR_CODE).setLongTermEnabled(true);
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnMissingBean
    public BlogAgent blogAgent(AiService aiService, ObjectProvider<AgentMemoryRegistry> registryProvider) {
        AgentMemoryRegistry registry = registryProvider.getIfAvailable();
        AgentMemory memory = registry != null && registry.contains(DefaultBlogAgent.AGENT_CODE)
                ? registry.get(DefaultBlogAgent.AGENT_CODE)
                : null;
        return new DefaultBlogAgent(memory, aiService);
    }

    /**
     * 系列生成编排器
     * 把「调用AI生成选题大纲 → 落库」建模为两节点 DAG，由带记忆的编排器驱动。
     * {@link BlogAgent} 通过 {@link ObjectProvider} 软依赖：AI运行时未接入时该Bean不存在，
     * 编排器仍可装配，仅在实际执行 generate-topics 节点时报 503。
     *
     * @param blogAgentProvider 博客Agent软依赖
     * @param outlineWriter     系列大纲落库器
     * @param orchestrator      DAG编排器（由 AiAgentAutoConfiguration 提供）
     * @param registryProvider  Agent记忆注册表软依赖
     * @return 系列生成编排器
     */
    @Bean
    @ConditionalOnMissingBean(name = "blogSeriesOrchestrator")
    public OrchestrationAgent blogSeriesOrchestrator(ObjectProvider<BlogAgent> blogAgentProvider,
                                                     BlogSeriesOutlineWriter outlineWriter,
                                                     Orchestrator orchestrator,
                                                     ObjectProvider<AgentMemoryRegistry> registryProvider) {
        OrchestrationGraph graph = OrchestrationGraph.builder(SERIES_ORCHESTRATOR_CODE)
                .node("generate-topics", ctx -> {
                    BlogAgent agent = blogAgentProvider.getIfAvailable();
                    if (agent == null) {
                        throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "AI服务尚未配置，无法生成系列主题");
                    }
                    BlogSeriesRequest request = ctx.get(CTX_REQUEST, BlogSeriesRequest.class);
                    ctx.put(CTX_OUTLINE, agent.generateSeriesTopics(request));
                })
                .node("persist", ctx -> {
                    BlogSeriesOutline outline = ctx.get(CTX_OUTLINE, BlogSeriesOutline.class);
                    Long seriesId = outlineWriter.write(outline, ctx.getString(CTX_TOPIC));
                    ctx.put(CTX_SERIES_ID, seriesId);
                    ctx.setConversationId(SERIES_ORCHESTRATOR_CODE + "-" + seriesId);
                })
                .edge("generate-topics", "persist")
                .build();

        AgentMemoryRegistry registry = registryProvider.getIfAvailable();
        AgentMemory memory = registry != null && registry.contains(SERIES_ORCHESTRATOR_CODE)
                ? registry.get(SERIES_ORCHESTRATOR_CODE)
                : null;
        return new OrchestrationAgent(SERIES_ORCHESTRATOR_CODE, memory, orchestrator, graph);
    }
}
