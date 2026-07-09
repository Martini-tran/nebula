package com.nebula.common.ai.config;

import com.nebula.common.ai.api.AiConversationRepository;
import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.memory.AgentMemoryConfig;
import com.nebula.common.ai.memory.AgentMemoryRegistry;
import com.nebula.common.ai.memory.DefaultAgentMemory;
import com.nebula.common.ai.memory.InMemoryAgentMemoryRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * AI记忆模块自动装配
 * 扫描容器中全部{@link AgentMemoryConfig} Bean，按其配置构建{@link DefaultAgentMemory}并注册进
 * {@link AgentMemoryRegistry}，使每个Agent获得各自独立的记忆配置。
 *
 * @author nebula
 */
@AutoConfiguration
public class AiMemoryAutoConfiguration {

    /**
     * 构建Agent记忆注册表，并自动注册全部已定义的Agent记忆配置
     *
     * @param agentMemoryConfigs     容器中全部Agent记忆配置
     * @param conversationRepository 短期会话历史仓储（可缺省）
     * @param longTermMemory         长期记忆（可缺省）
     * @return Agent记忆注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentMemoryRegistry agentMemoryRegistry(ObjectProvider<AgentMemoryConfig> agentMemoryConfigs,
                                                   ObjectProvider<AiConversationRepository> conversationRepository,
                                                   ObjectProvider<LongTermMemory> longTermMemory) {
        AgentMemoryRegistry registry = new InMemoryAgentMemoryRegistry();
        AiConversationRepository repository = conversationRepository.getIfAvailable();
        LongTermMemory memory = longTermMemory.getIfAvailable();
        agentMemoryConfigs.orderedStream()
                .forEach(config -> registry.register(new DefaultAgentMemory(config, repository, memory)));
        return registry;
    }
}
