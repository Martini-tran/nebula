package com.nebula.common.ai.rag.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * RAG 元数据落库层自动装配
 * 只要 classpath 具备 MyBatis 运行时即注册知识库三表 Mapper（{@code com.nebula.common.ai.rag.knowledge.store} 包），
 * <b>不受任何 RAG 场景开关门控</b>——知识库元数据 CRUD 与文档列表在向量库/embedding 未配置时仍需可读写（见 docs 第十一章
 * 降级：knowledge 未配置时仅关工具与导入/检索，元数据面照常）。向量运行时 Bean（KnowledgeService/工具）另由
 * {@link RagKnowledgeAutoConfiguration} 按开关装配。
 *
 * @author nebula
 */
@AutoConfiguration
@ConditionalOnClass(SqlSessionFactory.class)
@MapperScan("com.nebula.common.ai.rag.knowledge.store")
public class RagStoreAutoConfiguration {
}
