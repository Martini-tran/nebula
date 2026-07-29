package com.nebula.common.ai.rag.config;

import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.rag.memory.DatabaseLongTermMemory;
import com.nebula.common.ai.rag.memory.store.AiMemoryMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 长期记忆 DB 真相源自动装配（批次4 下沉）。
 *
 * <p>只要 classpath 具备 MyBatis 运行时即注册记忆 Mapper（{@code com.nebula.common.ai.rag.memory.store} 包）与
 * {@link DatabaseLongTermMemory}——记忆 DB 真相源是通用能力（manager 的 Agent 引擎、blog 的博客 Agent 都需要），
 * <b>不受任何 RAG 场景开关门控</b>，与 {@link RagStoreAutoConfiguration}（知识库元数据层）同理。
 *
 * <p>{@link ConditionalOnMissingBean}：业务服务若已自定义 {@link LongTermMemory} 实现则让位（不覆盖）。装饰器
 * {@code VectorLongTermMemory}（{@code mode=vector} 时装配）经 {@code ObjectProvider} 筛出本实现作被装饰的真相源，
 * 故本 Bean 与向量装饰是叠加关系，非互斥。
 *
 * @author nebula
 */
@AutoConfiguration
@ConditionalOnClass(SqlSessionFactory.class)
@MapperScan("com.nebula.common.ai.rag.memory.store")
public class RagMemoryStoreAutoConfiguration {

    /**
     * 数据库版长期记忆（真相源）。同时实现 {@code VectorReindexMarker}，承接批次4 对账置位/清位。
     *
     * @param aiMemoryMapper 记忆 Mapper
     * @return 数据库版长期记忆
     */
    @Bean
    @ConditionalOnMissingBean(LongTermMemory.class)
    public DatabaseLongTermMemory databaseLongTermMemory(AiMemoryMapper aiMemoryMapper) {
        return new DatabaseLongTermMemory(aiMemoryMapper);
    }
}
