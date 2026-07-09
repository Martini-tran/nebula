package com.nebula.common.ai.flow.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.config.FlowAutoConfiguration;
import com.nebula.common.ai.flow.ToolRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * AI工具同步自动装配
 * 把 SDK 装配的 {@link ToolRegistry}（代码中定义的全部工具）启动时镜像进 {@code ai_tool} 表。
 *
 * <p>独立于 {@link AiFlowStoreAutoConfiguration}（后者为 {@code before = FlowAutoConfiguration}）而声明为
 * {@code after = FlowAutoConfiguration}：本类依赖 {@link ToolRegistry}，必须在 SDK 的 {@code FlowAutoConfiguration}
 * 装配之后评估，{@code @ConditionalOnBean(ToolRegistry.class)} 才能正确命中。无 {@link ToolRegistry}
 * （即未启用 AI 运行时）时整体不装配，不影响纯落库场景。
 *
 * @author nebula
 */
@AutoConfiguration(after = FlowAutoConfiguration.class)
@ConditionalOnClass(SqlSessionFactory.class)
@ConditionalOnBean(ToolRegistry.class)
public class AiToolSyncAutoConfiguration {

    /**
     * 工具注册表 → ai_tool 表同步器：启动时把代码定义的工具镜像进表，缺失的内置工具软下线。
     *
     * @param toolRegistry 工具注册表（SDK 装配）
     * @param toolMapper   工具表 Mapper
     * @param objectMapper JSON 处理器（缺省自建）
     * @return 工具同步器
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistrySynchronizer toolRegistrySynchronizer(ToolRegistry toolRegistry,
                                                             AiToolMapper toolMapper,
                                                             ObjectProvider<ObjectMapper> objectMapper) {
        return new ToolRegistrySynchronizer(toolRegistry, toolMapper, objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
