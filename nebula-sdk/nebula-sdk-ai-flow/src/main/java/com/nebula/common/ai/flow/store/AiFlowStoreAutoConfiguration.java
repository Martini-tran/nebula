package com.nebula.common.ai.flow.store;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

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
@AutoConfiguration
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
}
