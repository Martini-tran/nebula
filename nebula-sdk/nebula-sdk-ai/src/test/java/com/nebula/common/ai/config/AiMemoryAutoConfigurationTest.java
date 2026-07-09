package com.nebula.common.ai.config;

import com.nebula.common.ai.memory.AgentMemoryConfig;
import com.nebula.common.ai.memory.AgentMemoryRegistry;
import com.nebula.common.ai.memory.MessageCountMemoryWindow;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMemoryAutoConfigurationTest {

    /**
     * 模拟业务侧为每个Agent定义各自的记忆配置Bean
     */
    @Configuration
    static class AgentConfigs {
        @Bean
        AgentMemoryConfig codeHelper() {
            return new AgentMemoryConfig("code-helper")
                    .setLongTermEnabled(true);
        }

        @Bean
        AgentMemoryConfig faq() {
            return new AgentMemoryConfig("faq")
                    .setWindow(new MessageCountMemoryWindow(10));
        }
    }

    @Test
    void registersAllAgentMemoryConfigBeans() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AiMemoryAutoConfiguration.class, AgentConfigs.class)) {

            AgentMemoryRegistry registry = context.getBean(AgentMemoryRegistry.class);

            assertEquals(2, registry.all().size());
            assertTrue(registry.contains("code-helper"));
            assertTrue(registry.contains("faq"));
            // 配置随Agent各自隔离
            assertTrue(registry.get("code-helper").config().isLongTermEnabled());
            assertSame(MessageCountMemoryWindow.class, registry.get("faq").config().getWindow().getClass());
        }
    }

    @Test
    void worksWithoutAnyAgentConfig() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AiMemoryAutoConfiguration.class)) {

            AgentMemoryRegistry registry = context.getBean(AgentMemoryRegistry.class);
            assertTrue(registry.all().isEmpty());
        }
    }
}
