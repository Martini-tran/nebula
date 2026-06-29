package com.nebula.common.ai.flow.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.config.FlowAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * AI流程落库层自动装配
 * 将三张流程表的 Mapper（{@code com.nebula.common.ai.flow.store} 包）注册到容器，并以数据库版
 * {@link DatabaseFlowDefinitionRepository} 顶替 SDK {@code FlowAutoConfiguration} 提供的默认内存仓储
 * {@code InMemoryFlowDefinitionRepository}（后者为 {@code @ConditionalOnMissingBean(FlowDefinitionRepository.class)}）。
 *
 * <p>仅在具备 MyBatis 运行时（classpath 存在 {@link SqlSessionFactory}）时装配，避免无数据源的模块误装。
 * 业务服务只需依赖本模块即可获得「DB 驱动的流程定义」，无需在各自启动类上额外 {@code @MapperScan} 本包。
 *
 * @author nebula
 */
@AutoConfiguration(before = FlowAutoConfiguration.class)
@ConditionalOnClass(SqlSessionFactory.class)
@MapperScan("com.nebula.common.ai.flow.store")
public class AiFlowStoreAutoConfiguration {

    /**
     * 数据库版流程定义仓储，从三表组装 FlowDefinition 供 FlowEngine 运行
     *
     * @param flowMapper 流程头 Mapper
     * @param nodeMapper 节点 Mapper
     * @param edgeMapper 边 Mapper
     * @return 数据库版流程定义仓储
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseFlowDefinitionRepository databaseFlowDefinitionRepository(AiFlowMapper flowMapper,
                                                                             AiFlowNodeMapper nodeMapper,
                                                                             AiFlowEdgeMapper edgeMapper) {
        return new DatabaseFlowDefinitionRepository(flowMapper, nodeMapper, edgeMapper);
    }

    /**
     * 数据库版模型档案仓储，从 {@code ai_model_profile} 表按编码加载并解密组装为 ModelProfile，
     * 覆盖 SDK 默认内存实现。apiKey 加解密密钥取自配置 {@code nebula.ai.profile.secret}（含默认值）。
     *
     * @param profileMapper 模型档案 Mapper
     * @param objectMapper  JSON 处理器（缺省自建）
     * @param environment   环境，用于读取加解密密钥
     * @return 数据库版模型档案仓储
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseModelProfileRepository databaseModelProfileRepository(AiModelProfileMapper profileMapper,
                                                                         ObjectProvider<ObjectMapper> objectMapper,
                                                                         Environment environment) {
        String secret = environment.getProperty("nebula.ai.profile.secret", "nebula-ai-profile-default-secret");
        return new DatabaseModelProfileRepository(profileMapper, objectMapper.getIfAvailable(ObjectMapper::new), secret);
    }
}
